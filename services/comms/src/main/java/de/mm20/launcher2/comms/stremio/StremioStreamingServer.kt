package de.mm20.launcher2.comms.stremio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** Uses the official streaming-server protocol, so Stremio owns torrent storage, index and eviction. */
object StremioStreamingServer {
    const val DEFAULT_ADDRESS = "http://127.0.0.1:11470/"
    private val hexHash = Regex("[0-9a-fA-F]{40}")
    private val base32Hash = Regex("[A-Za-z2-7]{32}")
    data class Settings(val cacheRoot: String, val cacheBytes: Long?, val version: String)

    fun normalizeAddress(address: String): String? = runCatching {
        val uri = URI(address.trim())
        require(uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https"))
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.port == -1 || uri.port in 1..65535)
        require(uri.path.isNullOrEmpty() || uri.path == "/")
        URI(uri.scheme.lowercase(Locale.ROOT), null, uri.host, uri.port, "/", null, null).toASCIIString()
    }.getOrNull()

    fun streamingUrl(address: String, stream: StreamItem): String? {
        val base = normalizeAddress(address) ?: return null
        val hash = stream.infoHash?.let(::canonicalHash) ?: return null
        val index = stream.fileIdx ?: -1
        if (index !in -1..65535) return null
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        val trackers = stream.sources.asSequence().filter { it.isNotBlank() && it.length <= 4096 }.take(32).map { "tr=" + encode(it) }
        val files = stream.fileMustInclude.asSequence().filter { it.isNotBlank() && it.length <= 512 }.take(32).map { "f=" + encode(it) }
        val query = (trackers + files).joinToString("&")
        return "$base$hash/$index" + if (query.isEmpty()) "" else "?$query"
    }

    private fun canonicalHash(hash: String): String? {
        val value = hash.trim()
        if (hexHash.matches(value)) return value.lowercase(Locale.ROOT)
        if (!base32Hash.matches(value)) return null
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var accumulator = 0; var bits = 0
        val bytes = ArrayList<Byte>(20)
        for (char in value.uppercase(Locale.ROOT)) {
            accumulator = (accumulator shl 5) or alphabet.indexOf(char); bits += 5
            if (bits >= 8) { bits -= 8; bytes += (accumulator shr bits).toByte(); accumulator = accumulator and ((1 shl bits) - 1) }
        }
        return bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private suspend fun request(address: String, body: JSONObject? = null): JSONObject {
        val base = normalizeAddress(address) ?: error("Invalid streaming server")
        val connection = URL(base + "settings").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000; connection.readTimeout = 8_000
            if (body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            check(connection.responseCode in 200..299)
            check(connection.contentLengthLong <= 128 * 1024 || connection.contentLengthLong < 0)
            val bytes = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    check(bytes.size() + n <= 128 * 1024)
                    bytes.write(buffer, 0, n)
                }
            }
            return JSONObject(bytes.toString("UTF-8"))
        } finally { connection.disconnect() }
    }

    private fun parse(response: JSONObject): Settings {
        val values = response.getJSONObject("values")
        val root = values.getString("cacheRoot").take(4096)
        val version = values.getString("serverVersion").take(128)
        require(root.isNotBlank() && version.isNotBlank())
        return Settings(root, values.optDouble("cacheSize", Double.NaN).takeIf { it.isFinite() && it >= 0 && it <= Long.MAX_VALUE }?.toLong(), version)
    }

    suspend fun settings(address: String): Settings = withContext(Dispatchers.IO) { parse(request(address)) }

    /** Explicit user action: updates the server shared by both clients and verifies the resulting values. */
    suspend fun configure(address: String, cacheGb: Int, root: String?): Settings = withContext(Dispatchers.IO) {
        require(cacheGb in StremioPlaybackSettings.cacheChoices)
        require(root == null || root.length in 1..4096 && root.none { it.isISOControl() })
        val current = request(address).getJSONObject("values")
        val body = JSONObject()
        // Preserve the official server's other settings instead of resetting connection/speed options.
        for (key in listOf("btMaxConnections", "btHandshakeTimeout", "btRequestTimeout", "btDownloadSpeedSoftLimit", "btDownloadSpeedHardLimit", "btMinPeersForStable", "remoteHttps", "proxyStreamsEnabled", "transcodeProfile")) {
            if (current.has(key)) body.put(key, current.get(key))
        }
        val bytes = cacheGb.toLong() * 1024 * 1024 * 1024
        body.put("cacheSize", bytes)
        if (root != null && root != current.optString("cacheRoot")) body.put("cacheRoot", root)
        val ack = request(address, body)
        check(ack.optBoolean("success", false))
        settings(address).also {
            check(it.cacheBytes == bytes)
            if (root != null) check(it.cacheRoot == root)
        }
    }
}
