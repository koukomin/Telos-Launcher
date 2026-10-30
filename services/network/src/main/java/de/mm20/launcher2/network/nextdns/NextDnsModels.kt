package de.mm20.launcher2.network.nextdns

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Public state never contains the API credential. */
data class NextDnsCredentialsInfo(val profileId: String?, val hasKey: Boolean)
data class NextDnsProfile(val id: String, val name: String)
data class NextDnsLog(val timestamp: String, val domain: String, val status: String,
    val deviceName: String? = null, val reasons: List<String> = emptyList(), val protocol: String? = null)
data class NextDnsRule(val id: String, val active: Boolean)
data class NextDnsRewrite(val id: String, val name: String, val type: String, val content: String)
data class NextDnsPage<T>(val items: List<T>, val cursor: String?)
enum class NextDnsSettingsPage(val apiPath: String) {
    SECURITY("security"),
    PRIVACY("privacy"),
    PARENTAL_CONTROL("parentalControl"),
    GENERAL("settings"),
}
data class NextDnsSettings(val page: NextDnsSettingsPage, val values: JsonObject)
data class NextDnsBooleanSetting(val path: List<String>, val value: Boolean)

enum class NextDnsAnalyticsPeriod(val queryValue: String) {
    MINUTES_30("-30m"),
    HOURS_6("-6h"),
    HOURS_24("-24h"),
    DAYS_7("-7d"),
    DAYS_30("-30d"),
    MONTHS_3("-3M"),
}

enum class NextDnsAnalyticsMetric(
    val id: String,
    val feature: String,
    val filters: Map<String, String> = emptyMap(),
    val series: Boolean = false,
    val displayLimit: Int? = null,
) {
    TRAFFIC("status", "status", series = true),
    RESOLVED_DOMAINS("domains.resolved", "domains", mapOf("status" to "default,allowed", "limit" to "6"), displayLimit = 6),
    BLOCKED_DOMAINS("domains.blocked", "domains", mapOf("status" to "blocked", "limit" to "6"), displayLimit = 6),
    ROOT_DOMAINS("domains.root", "domains", mapOf("root" to "true", "limit" to "6"), displayLimit = 6),
    BLOCK_REASONS("reasons", "reasons", mapOf("limit" to "6", "lang" to "en"), displayLimit = 6),
    DEVICES("devices", "devices", mapOf("limit" to "4"), displayLimit = 4),
    IP_ADDRESSES("ips", "ips", mapOf("limit" to "4", "lang" to "en"), displayLimit = 4),
    IP_VERSIONS("ipVersions", "ipVersions", displayLimit = 6),
    DESTINATIONS("destinations.gafam", "destinations", mapOf("type" to "gafam")),
    COUNTRIES(
        "destinations.countries",
        "destinations",
        mapOf("type" to "countries", "limit" to "20", "lang" to "en"),
        displayLimit = 8,
    ),
    ENCRYPTION("encryption", "encryption"),
    DNSSEC("dnssec", "dnssec"),
    QUERY_TYPES("queryTypes", "queryTypes", mapOf("limit" to "6"), displayLimit = 6),
    PROTOCOLS("protocols", "protocols", mapOf("limit" to "6"), displayLimit = 6),
}

data class NextDnsAnalyticsRow(val label: String, val value: String, val details: String? = null)
data class NextDnsAnalyticsSnapshot(
    val metric: NextDnsAnalyticsMetric,
    val totalQueries: Long,
    val rows: List<NextDnsAnalyticsRow>,
    val series: List<Long> = emptyList(),
    val nextCursor: String? = null,
)
data class NextDnsAnalyticsDevice(val id: String, val name: String)

internal object NextDnsAnalyticsParser {
    private const val MAX_ROWS = 100
    private const val MAX_STRING = 512

    fun snapshot(metric: NextDnsAnalyticsMetric, root: JsonObject, cursor: String?): NextDnsAnalyticsSnapshot {
        val rawData = root["data"] ?: root["series"] ?: root.takeIf { it.containsKey("queries") }
            ?: throw NextDnsException(NextDnsErrorKind.API)
        val dataObject = rawData as? JsonObject
        val data = when (rawData) {
            is JsonArray -> rawData
            is JsonObject -> (rawData["data"] as? JsonArray)
                ?: (rawData["series"] as? JsonArray)
                ?: if (rawData.containsKey("queries")) JsonArray(listOf(rawData)) else JsonArray(emptyList())
            else -> throw NextDnsException(NextDnsErrorKind.API)
        }
        if (data.size > MAX_ROWS) throw NextDnsException(NextDnsErrorKind.API)
        val series = dataObject?.let(::querySeries).orEmpty().ifEmpty { aggregateSeries(data) }
        val rawRows = data.mapNotNull { value ->
            val row = value as? JsonObject ?: return@mapNotNull null
            val label = listOf(
                "domain",
                "name",
                "device",
                "ip",
                "code",
                "company",
                "country",
                "protocol",
                "status",
                "id",
            )
                .firstNotNullOfOrNull { row.string(it) }
                ?: (row["version"] as? JsonPrimitive)?.intOrNull?.let { "IPv$it" }
                ?: row["encrypted"]?.booleanValue()?.let { if (it) "encrypted" else "unencrypted" }
                ?: row["validated"]?.booleanValue()?.let { if (it) "validated" else "unvalidated" }
                ?: metric.id
            val queries = row.long("queries")?.coerceAtLeast(0)
                ?: row.queryArray()?.let { querySeries(row).asSequence().saturatingSum() }
            val details = row.entries.asSequence()
                .filterNot {
                    it.key in setOf(
                        "domain", "name", "device", "ip", "code", "company", "country", "protocol",
                        "status", "id", "version", "queries", "encrypted", "validated",
                    )
                }
                .take(8)
                .mapNotNull { (key, item) ->
                    (item as? JsonPrimitive)?.contentOrNull?.take(MAX_STRING)?.let { "$key: $it" }
                }
                .joinToString(" · ")
                .ifEmpty { null }
            NextDnsAnalyticsRow(label.take(MAX_STRING), queries?.toString() ?: "", details)
        }
        val totalForPercent = rawRows.asSequence()
            .mapNotNull { it.value.toLongOrNull() }
            .saturatingSum()
        val rows = when (metric) {
            NextDnsAnalyticsMetric.ENCRYPTION, NextDnsAnalyticsMetric.DNSSEC -> rawRows.map { row ->
                val flag = if (metric == NextDnsAnalyticsMetric.ENCRYPTION) "encrypted" else "validated"
                val enabled = row.label == flag
                val queries = row.value.toLongOrNull() ?: 0L
                val percent = if (totalForPercent == 0L) 0.0 else queries.toDouble() * 100.0 / totalForPercent
                row.copy(
                    value = "$queries (${String.format(java.util.Locale.ROOT, "%.1f%%", percent)})",
                    details = null,
                    label = if (enabled) flag else "not $flag",
                )
            }
            NextDnsAnalyticsMetric.DESTINATIONS -> rawRows.map { row ->
                val queries = row.value.toLongOrNull() ?: 0L
                val percent = if (totalForPercent == 0L) 0.0 else queries.toDouble() * 100.0 / totalForPercent
                row.copy(value = String.format(java.util.Locale.ROOT, "%.1f%%", percent))
            }
            else -> rawRows
        }
        val total = if (series.isNotEmpty()) series.asSequence().saturatingSum()
            else rawRows.asSequence().mapNotNull { it.value.toLongOrNull() }.saturatingSum()
        return NextDnsAnalyticsSnapshot(
            metric,
            total.coerceAtLeast(0),
            rows.take(if (cursor == null) metric.displayLimit ?: MAX_ROWS else MAX_ROWS),
            series.take(500),
            cursor,
        )
    }

    fun devices(root: JsonObject): List<NextDnsAnalyticsDevice> {
        val rows = (root["data"] as? JsonArray) ?: throw NextDnsException(NextDnsErrorKind.API)
        if (rows.size > 200) throw NextDnsException(NextDnsErrorKind.API)
        return rows.map { value ->
            val row = value as? JsonObject ?: throw NextDnsException(NextDnsErrorKind.API)
            val id = row.string("id")?.takeIf { it.isNotBlank() }
                ?: throw NextDnsException(NextDnsErrorKind.API)
            NextDnsAnalyticsDevice(id.take(128), (row.string("name") ?: id).take(MAX_STRING))
        }
    }

    private fun querySeries(row: JsonObject): List<Long> =
        row.queryArray()?.let { values ->
            if (values.size > 500) throw NextDnsException(NextDnsErrorKind.API)
            values.map { (it as? JsonPrimitive)?.longOrNull?.coerceAtLeast(0L) ?: 0L }
        }.orEmpty()

    private fun aggregateSeries(rows: JsonArray): List<Long> {
        val series = rows.mapNotNull { (it as? JsonObject)?.let(::querySeries) }
        val maxLength = series.maxOfOrNull { it.size } ?: return emptyList()
        return (0 until maxLength.coerceAtMost(500)).map { index ->
            series.asSequence().mapNotNull { it.getOrNull(index) }.saturatingSum()
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.length <= MAX_STRING }

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.queryArray(): JsonArray? =
        ((this["queries"] ?: this["series"]) as? JsonArray)

    private fun JsonElement.booleanValue(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

    private fun Sequence<Long>.saturatingSum(): Long = fold(0L) { total, value ->
        if (Long.MAX_VALUE - total < value) Long.MAX_VALUE else total + value
    }
}

object NextDnsRewriteValidation {
    fun name(value: String): String = NextDnsValidation.domain(value)

    fun content(value: String): String {
        val address = value.trim()
        if (address.length > 253 || address.any { it.isWhitespace() || it.isISOControl() }) {
            throw NextDnsException(NextDnsErrorKind.INVALID)
        }
        if (isIpv4(address) || isIpv6(address)) return address
        return NextDnsValidation.domain(address)
    }

    private fun isIpv4(value: String): Boolean {
        val octets = value.split('.')
        return octets.size == 4 && octets.all { octet ->
            octet.isNotEmpty() && octet.all(Char::isDigit) && octet.toIntOrNull() in 0..255
        }
    }

    private fun isIpv6(value: String): Boolean {
        return !(value.length !in 2..45 || ':' !in value || value.any { it !in "0123456789abcdefABCDEF:." }) && try {
            java.net.InetAddress.getByName(value) is java.net.Inet6Address
        } catch (_: java.net.UnknownHostException) {
            false
        }
    }
}

object NextDnsSettingsValues {
    private const val MAX_DEPTH = 8
    private const val MAX_SETTINGS = 256

    fun booleans(values: JsonObject): List<NextDnsBooleanSetting> {
        val settings = ArrayList<NextDnsBooleanSetting>()
        collect(values, emptyList(), settings)
        return settings
    }

    fun patch(path: List<String>, value: Boolean): JsonObject {
        require(path.isNotEmpty() && path.size <= MAX_DEPTH)
        require(path.all { it.isNotBlank() && it.length <= 128 && it.none(Char::isISOControl) })
        var element: JsonElement = JsonPrimitive(value)
        for (key in path.asReversed()) {
            element = JsonObject(mapOf(key to element))
        }
        return element as JsonObject
    }

    private fun collect(
        values: JsonObject,
        prefix: List<String>,
        output: MutableList<NextDnsBooleanSetting>,
    ) {
        if (prefix.size >= MAX_DEPTH) return
        values.forEach { (key, value) ->
            if (output.size >= MAX_SETTINGS || key.isBlank() || key.length > 128) return
            val path = prefix + key
            when (value) {
                is JsonObject -> collect(value, path, output)
                is JsonPrimitive -> value.booleanOrNull?.let {
                    if (output.size < MAX_SETTINGS) output += NextDnsBooleanSetting(path, it)
                }
                else -> Unit
            }
        }
    }
}
enum class NextDnsErrorKind { AUTH, INVALID, RATE_LIMIT, NETWORK, API, UNCONFIGURED }
/** Do not propagate server error bodies, keys, query domains or URLs into crash reports. */
class NextDnsException(val kind: NextDnsErrorKind) : Exception("NextDNS ${kind.name}")
