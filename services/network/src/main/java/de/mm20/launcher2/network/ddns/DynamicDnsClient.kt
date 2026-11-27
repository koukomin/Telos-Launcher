package de.mm20.launcher2.network.ddns

import java.net.Inet6Address
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class DynamicDnsProvider(val label: String, internal val endpoint: String) {
    DuckDns("DuckDNS", "https://www.duckdns.org/update"),
    NoIp("No-IP", "https://dynupdate.no-ip.com/nic/update"),
    Dyn("Dyn", "https://members.dyndns.org/nic/update"),
    Dynu("Dynu", "https://api.dynu.com/nic/update"),
    FreeDns("FreeDNS", "https://freedns.afraid.org/dynamic/update.php"),
    Cloudflare("Cloudflare", "https://api.cloudflare.com/client/v4/"),
}

object DynamicDnsClient {
    enum class Result { Updated, Unchanged, Rejected, Unreachable, Invalid }

    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val hostname = Regex("(?=.{1,253}$)[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+")
    private val cloudId = Regex("[a-fA-F0-9]{32}")
    private val freeDnsKey = Regex("[a-zA-Z0-9_+/=-]{1,256}")

    suspend fun update(
        provider: DynamicDnsProvider,
        host: String,
        address: String,
        secret: String,
        account: String = "",
        record: String = "",
    ): Result = withContext(Dispatchers.IO) {
        val request = request(provider, host.trim(), address.trim(), secret, account.trim(), record.trim())
            ?: return@withContext Result.Invalid
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.Rejected
                val body = response.body.source()
                if (body.request(8193)) return@withContext Result.Rejected
                val value = body.readUtf8().trim()
                when (provider) {
                    DynamicDnsProvider.Cloudflare ->
                        if (runCatching { JSONObject(value).optBoolean("success") }.getOrDefault(false)) Result.Updated else Result.Rejected
                    DynamicDnsProvider.DuckDns -> if (value == "OK") Result.Updated else Result.Rejected
                    DynamicDnsProvider.FreeDns -> when {
                        value.startsWith("Updated ") -> Result.Updated
                        value.startsWith("ERROR: Address ") && value.contains("has not changed") -> Result.Unchanged
                        else -> Result.Rejected
                    }
                    else -> when (value.substringBefore(' ')) {
                        "good" -> Result.Updated
                        "nochg" -> Result.Unchanged
                        else -> Result.Rejected
                    }
                }
            }
        } catch (_: java.io.IOException) {
            Result.Unreachable
        }
    }

    internal fun request(
        provider: DynamicDnsProvider,
        host: String,
        address: String,
        secret: String,
        account: String,
        record: String,
    ): Request? {
        if (!hostname.matches(host) || !validAddress(address) ||
            secret.isBlank() || secret.length > 512 || secret.any { it.code !in 33..126 }
        ) return null
        val url = provider.endpoint.toHttpUrl().newBuilder()
        val builder = Request.Builder().header("User-Agent", "TelosLauncher-DynamicDNS/1.0")
        when (provider) {
            DynamicDnsProvider.Cloudflare -> {
                if (!cloudId.matches(account) || !cloudId.matches(record)) return null
                url.addPathSegments("zones/$account/dns_records/$record")
                val body = JSONObject().put("name", host).put("content", address)
                    .put("type", if (':' in address) "AAAA" else "A")
                builder.header("Authorization", "Bearer $secret")
                    .patch(body.toString().toRequestBody("application/json".toMediaType()))
            }
            DynamicDnsProvider.DuckDns -> {
                val subdomain = host.lowercase().removeSuffix(".duckdns.org")
                if ('.' in subdomain || !host.endsWith(".duckdns.org", ignoreCase = true)) return null
                url.addQueryParameter("domains", subdomain).addQueryParameter("token", secret)
                    .addQueryParameter(if (':' in address) "ipv6" else "ip", address)
            }
            DynamicDnsProvider.FreeDns -> {
                if (!freeDnsKey.matches(secret) || ':' in address) return null
                url.addQueryParameter(secret, null).addQueryParameter("address", address)
            }
            else -> {
                if (account.isBlank() || account.length > 256 || account.any { it.isISOControl() || it == ':' }) return null
                builder.header("Authorization", Credentials.basic(account, secret))
                url.addQueryParameter("hostname", host).addQueryParameter("myip", address)
            }
        }
        return builder.url(url.build()).build()
    }

    private fun validAddress(value: String): Boolean {
        if (':' in value) return value.length <= 45 && value.all { it in "0123456789abcdefABCDEF:." } &&
            runCatching { InetAddress.getByName(value) is Inet6Address }.getOrDefault(false)
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.length in 1..3 && part.all { it in '0'..'9' } && part.toInt() <= 255 &&
                (part.length == 1 || part[0] != '0')
        }
    }
}
