package de.mm20.launcher2.network.nextdns

import java.net.URLEncoder
import kotlinx.serialization.json.*

/** Implements https://nextdns.github.io/api/ without assuming HTTP 200 means application success. */
internal class NextDnsApi(private val transport: NextDnsTransport) {
    suspend fun profile(profile: String, key: String): NextDnsProfile {
        val id = NextDnsValidation.profileId(profile)
        val data = request("GET", "/profiles/$id", key).objectValue()
        return NextDnsProfile(id, data.string("name", 512) ?: id)
    }
    suspend fun logs(profile: String, key: String, search: String, status: String?, cursor: String?): NextDnsPage<NextDnsLog> {
        val id = NextDnsValidation.profileId(profile)
        if (search.length > 256 || status != null && status !in setOf("default", "error", "blocked", "allowed"))
            throw NextDnsException(NextDnsErrorKind.INVALID)
        val query = mutableListOf("limit=100", "sort=desc")
        if (search.isNotBlank()) query += "search=${encode(search.trim())}"
        status?.let { query += "status=$it" }
        cursor?.let { query += "cursor=${encode(validCursor(it))}" }
        val response = request("GET", "/profiles/$id/logs?${query.joinToString("&")}", key)
        val items = response.data.arrayValue().also { if (it.size > 1000) malformed() }.map { value ->
            val row = value.objectValue()
            val device = row["device"] as? JsonObject
            val reasons = (row["reasons"] as? JsonArray).orEmpty().take(32).mapNotNull {
                (it as? JsonObject)?.let { reason -> reason.string("name", 512) ?: reason.string("id", 512) }
            }
            NextDnsLog(row.requiredString("timestamp", 128), row.requiredString("domain", 253),
                row.string("status", 32) ?: "default", device?.string("name", 512), reasons, row.string("protocol", 64))
        }
        return NextDnsPage(items, response.cursor())
    }
    suspend fun rules(profile: String, key: String, allow: Boolean): List<NextDnsRule> {
        val id = NextDnsValidation.profileId(profile)
        val result = mutableListOf<NextDnsRule>()
        val cursors = mutableSetOf<String>()
        var cursor: String? = null
        do {
            val query = "?limit=100" + (cursor?.let { "&cursor=${encode(it)}" } ?: "")
            val response = request("GET", "/profiles/$id/${listName(allow)}$query", key)
            val rows = response.data.arrayValue()
            if (result.size + rows.size > 2000) malformed()
            result += rows.map {
                val row = it.objectValue()
                val domain = row.requiredString("id", 253)
                val active = (row["active"] as? JsonPrimitive)?.booleanOrNull ?: malformed()
                NextDnsRule(domain, active)
            }
            cursor = response.cursor()
            if (cursor != null && !cursors.add(cursor)) malformed()
        } while (cursor != null)
        return result.distinctBy { it.id }
    }
    suspend fun putRule(profile: String, key: String, allow: Boolean, domain: String) {
        val id = NextDnsValidation.profileId(profile)
        val normalized = NextDnsValidation.domain(domain)
        request("POST", "/profiles/$id/${listName(allow)}", key,
            buildJsonObject { put("id", normalized); put("active", true) }.toString(), mutation = true)
    }
    suspend fun setRuleActive(profile: String, key: String, allow: Boolean, domain: String, active: Boolean) {
        ruleMutation("PATCH", profile, key, allow, domain, buildJsonObject { put("active", active) }.toString())
    }
    suspend fun deleteRule(profile: String, key: String, allow: Boolean, domain: String) {
        ruleMutation("DELETE", profile, key, allow, domain, null)
    }
    private suspend fun ruleMutation(method: String, profile: String, key: String, allow: Boolean, domain: String, body: String?) {
        val id = NextDnsValidation.profileId(profile)
        val normalized = NextDnsValidation.domain(domain)
        request(method, "/profiles/$id/${listName(allow)}/${encode(normalized)}", key, body, mutation = true)
    }
    private suspend fun request(method: String, path: String, key: String, body: String? = null, mutation: Boolean = false): Envelope {
        val response = transport.exchange(NextDnsRequest(method, path, NextDnsValidation.apiKey(key), body))
        if (response.status == 401 || response.status == 403) throw NextDnsException(NextDnsErrorKind.AUTH)
        if (response.status == 429) throw NextDnsException(NextDnsErrorKind.RATE_LIMIT)
        if (response.body.toByteArray(Charsets.UTF_8).size > 1_048_576) malformed()
        if (response.status == 204 && mutation) return Envelope(JsonNull, null)
        val json = try { Json.parseToJsonElement(response.body).jsonObject }
            catch (_: Exception) { malformed() }
        val errors = json["errors"]
        if (errors != null) {
            val list = errors as? JsonArray ?: malformed()
            if (list.isNotEmpty()) {
                val codes = list.take(32).mapNotNull { (it as? JsonObject)?.string("code", 128) }
                throw NextDnsException(when {
                    codes.any { it in setOf("unauthorized", "forbidden", "unauthenticated") } -> NextDnsErrorKind.AUTH
                    codes.any { it in setOf("invalid", "invalid_argument", "invalid_request") } -> NextDnsErrorKind.INVALID
                    else -> NextDnsErrorKind.API
                })
            }
        }
        if (response.status !in 200..299) throw NextDnsException(if (response.status == 400) NextDnsErrorKind.INVALID else NextDnsErrorKind.API)
        val data = json["data"] ?: if (mutation) JsonNull else malformed()
        return Envelope(data, json["meta"] as? JsonObject)
    }
    private data class Envelope(val data: JsonElement, val meta: JsonObject?) {
        fun objectValue() = data.objectValue()
        fun cursor(): String? {
            val raw = ((meta?.get("pagination") as? JsonObject)?.get("cursor")) ?: return null
            if (raw is JsonNull) return null
            return (raw as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::validCursor) ?: malformed()
        }
    }
    private companion object {
        fun malformed(): Nothing = throw NextDnsException(NextDnsErrorKind.API)
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
        fun listName(allow: Boolean) = if (allow) "allowlist" else "denylist"
        fun validCursor(value: String): String = value.also {
            if (it.isEmpty() || it.length > 2048 || it.any(Char::isISOControl)) throw NextDnsException(NextDnsErrorKind.API)
        }
        fun JsonElement.objectValue(): JsonObject = this as? JsonObject ?: malformed()
        fun JsonElement.arrayValue(): JsonArray = this as? JsonArray ?: malformed()
        fun JsonObject.string(name: String, max: Int): String? = when (val value = get(name)) {
            null, JsonNull -> null
            is JsonPrimitive -> if (value.isString && value.content.length <= max) value.content else malformed()
            else -> malformed()
        }
        fun JsonObject.requiredString(name: String, max: Int): String = string(name, max)?.takeIf { it.isNotEmpty() } ?: malformed()
    }
}
