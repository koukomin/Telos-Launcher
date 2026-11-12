package de.mm20.launcher2.ui.desktopsync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/**
 * Android LAN pairing client for Telos Desktop (VOS-4).
 *
 * Speaks the same URI / HTTP contract as desktop `LanPairProtocol` /
 * `LanPairingServer`. The session bundle is the AES-256 zip4j archive
 * (`telos-desktop-session.telos.zip`) — Android stores/transfers the encrypted
 * bytes and does not log pairing codes or tokens.
 */
object DesktopLanPairClient {
    const val SCHEME = "telos-sync"
    const val DEFAULT_PORT = 17890
    const val BUNDLE_NAME = "telos-desktop-session.telos.zip"
    private const val PREFS = "telos_desktop_lan_pair"
    private const val KEY_TOKEN = "pair_token"
    private const val KEY_HOST = "pair_host"
    private const val KEY_PORT = "pair_port"
    private const val KEY_DEVICE = "device_id"
    private const val TAG = "TelosLanPair"
    private const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    data class PairPayload(
        val code: String,
        val host: String,
        val port: Int,
        val tls: Boolean = false,
    ) {
        fun httpBaseUrl(): String {
            val scheme = if (tls) "https" else "http"
            val hostPart = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
            return "$scheme://$hostPart:$port"
        }
    }

    data class PairResult(
        val token: String,
        val expiresAtEpochMs: Long,
        val bundleName: String,
    )

    fun normalizeCode(raw: String): String =
        raw.trim().uppercase(Locale.US).filter { it in CODE_ALPHABET }

    fun buildUri(code: String, host: String, port: Int, tls: Boolean = false): String {
        val normalized = normalizeCode(code)
        require(normalized.length >= 4)
        require(port in 1..65535)
        val enc = StandardCharsets.UTF_8.name()
        val q = buildString {
            append("code=").append(URLEncoder.encode(normalized, enc))
            append("&host=").append(URLEncoder.encode(host.trim(), enc))
            append("&port=").append(port)
            if (tls) append("&tls=1")
        }
        return "$SCHEME://pair?$q"
    }

    fun parseUri(raw: String): PairPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val uri = URI(trimmed)
            if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return@runCatching null
            val query = uri.rawQuery
                ?: uri.schemeSpecificPart.substringAfter('?', missingDelimiterValue = "")
            if (query.isBlank()) return@runCatching null
            val params = query.split('&').mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val enc = StandardCharsets.UTF_8.name()
                URLDecoder.decode(part.substring(0, eq), enc) to
                    URLDecoder.decode(part.substring(eq + 1), enc)
            }.toMap()
            val code = normalizeCode(params["code"].orEmpty())
            val host = params["host"]?.trim().orEmpty()
            val port = params["port"]?.toIntOrNull() ?: DEFAULT_PORT
            val tls = params["tls"] == "1" || params["tls"].equals("true", ignoreCase = true)
            if (code.length < 4 || host.isBlank() || port !in 1..65535) return@runCatching null
            PairPayload(code, host, port, tls)
        }.getOrNull()
    }

    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE, null)
        if (!existing.isNullOrBlank()) return existing
        val id = "android-" + UUID.randomUUID().toString().take(8)
        prefs.edit().putString(KEY_DEVICE, id).apply()
        return id
    }

    fun storedToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun clearToken(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TOKEN)
            .remove(KEY_HOST)
            .remove(KEY_PORT)
            .apply()
    }

    suspend fun pair(
        context: Context,
        payload: PairPayload,
        deviceName: String = "Telos Android",
    ): Result<PairResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(payload.httpBaseUrl() + "/v1/pair")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Telos-Pair-Code", payload.code)
                // Never log the pairing code.
            }
            val body = """{"code":"${esc(payload.code)}","deviceId":"${esc(deviceId(context))}","deviceName":"${esc(deviceName)}"}"""
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            conn.disconnect()
            if (code !in 200..299) {
                Log.w(TAG, "pair failed status=$code")
                error("pair_failed:$code")
            }
            val token = extractJsonString(text, "token") ?: error("missing_token")
            val expires = Regex(""""expiresAt"\s*:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val bundleName = extractJsonString(text, "bundleName") ?: BUNDLE_NAME
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_HOST, payload.host)
                .putInt(KEY_PORT, payload.port)
                .apply()
            Log.i(TAG, "paired with desktop host=${payload.host} port=${payload.port}")
            PairResult(token = token, expiresAtEpochMs = expires, bundleName = bundleName)
        }
    }

    suspend fun fetchDesktopBundle(context: Context, dest: File? = null): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val token = prefs.getString(KEY_TOKEN, null) ?: error("not_paired")
                val host = prefs.getString(KEY_HOST, null) ?: error("not_paired")
                val port = prefs.getInt(KEY_PORT, DEFAULT_PORT)
                val url = URL("http://$host:$port/v1/bundle")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 60_000
                    setRequestProperty("Authorization", "Bearer $token")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    Log.w(TAG, "fetch bundle failed status=$code")
                    conn.disconnect()
                    error("fetch_failed:$code")
                }
                val out = dest ?: File(context.filesDir, "desktop-sync/$BUNDLE_NAME").also {
                    it.parentFile?.mkdirs()
                }
                conn.inputStream.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                conn.disconnect()
                Log.i(TAG, "fetched desktop bundle bytes=${out.length()}")
                out
            }
        }

    suspend fun postEncryptedBundle(context: Context, bundle: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(bundle.isFile) { "missing_bundle" }
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val token = prefs.getString(KEY_TOKEN, null) ?: error("not_paired")
                val host = prefs.getString(KEY_HOST, null) ?: error("not_paired")
                val port = prefs.getInt(KEY_PORT, DEFAULT_PORT)
                val url = URL("http://$host:$port/v1/bundle")
                val bytes = bundle.readBytes()
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/octet-stream")
                    setRequestProperty("Authorization", "Bearer $token")
                    setFixedLengthStreamingMode(bytes.size)
                }
                conn.outputStream.use { it.write(bytes) }
                val code = conn.responseCode
                conn.disconnect()
                if (code !in 200..299) {
                    Log.w(TAG, "post bundle failed status=$code")
                    error("post_failed:$code")
                }
                Log.i(TAG, "posted encrypted bundle bytes=${bytes.size}")
                Unit
            }
        }

    private fun extractJsonString(body: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        return pattern.find(body)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }

    private fun esc(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
