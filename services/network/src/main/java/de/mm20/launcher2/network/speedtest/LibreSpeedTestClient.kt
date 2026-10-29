package de.mm20.launcher2.network.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

data class LibreSpeedServer(
    val name: String,
    val pingUrl: HttpUrl,
    val downloadUrl: HttpUrl,
    val uploadUrl: HttpUrl,
)

data class NetworkSpeedTestResult(
    val serverName: String,
    val pingMs: Long,
    val jitterMs: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
)

enum class NetworkSpeedTestPhase {
    PING,
    DOWNLOAD,
    UPLOAD,
}

class LibreSpeedTestClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private suspend fun <T> execute(request: Request, block: (Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val result = block(it)
                            if (continuation.isActive) continuation.resumeWith(Result.success(result))
                        } catch (error: Throwable) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                }
            })
        }

    suspend fun loadPublicServers(): List<LibreSpeedServer> {
        val request = Request.Builder()
            .url(SERVER_LIST_URL)
            .header("Cache-Control", "no-cache")
            .get()
            .build()
        return execute(request) { response ->
            require(response.isSuccessful) { "Server list request failed (${response.code})" }
            val body = response.body
            val source = body.source()
            val buffer = Buffer()
            var total = 0L
            while (total < MAX_SERVER_LIST_BYTES) {
                val read = source.read(buffer, minOf(8_192L, MAX_SERVER_LIST_BYTES - total))
                if (read == -1L) break
                total += read
            }
            require(total < MAX_SERVER_LIST_BYTES || source.exhausted()) { "Server list is too large" }
            parsePublicServers(buffer.readUtf8())
        }
    }

    suspend fun run(
        server: LibreSpeedServer,
        onPhaseChanged: (NetworkSpeedTestPhase) -> Unit,
    ): NetworkSpeedTestResult {
        onPhaseChanged(NetworkSpeedTestPhase.PING)
        val pings = LongArray(PING_SAMPLES)
        repeat(PING_SAMPLES) { index ->
            val url = server.pingUrl.newBuilder()
                .addQueryParameter("cors", "true")
                .addQueryParameter("r", Random.nextLong().toString())
                .build()
            val started = System.nanoTime()
            val request = Request.Builder().url(url).get().build()
            execute(request) { response ->
                require(response.isSuccessful) { "Ping request failed (${response.code})" }
                pings[index] = elapsedMillis(started)
            }
        }

        onPhaseChanged(NetworkSpeedTestPhase.DOWNLOAD)
        var downloaded = 0L
        var downloadTime = 0L
        repeat(DOWNLOAD_SAMPLES) {
            val url = server.downloadUrl.newBuilder()
                .addQueryParameter("ckSize", DOWNLOAD_MEGABYTES.toString())
                .addQueryParameter("cors", "true")
                .addQueryParameter("r", Random.nextLong().toString())
                .build()
            val started = System.nanoTime()
            val request = Request.Builder().url(url).get().build()
            val bytes = execute(request) { response ->
                require(response.isSuccessful) { "Download request failed (${response.code})" }
                val source = response.body.source()
                readBoundedBytes(source, MAX_DOWNLOAD_BYTES_PER_SAMPLE)
            }
            downloaded += bytes
            downloadTime += elapsedMillis(started)
        }
        require(downloaded > 0L) { "Download returned no data" }

        onPhaseChanged(NetworkSpeedTestPhase.UPLOAD)
        val uploadPayload = withContext(Dispatchers.Default) {
            ByteArray(UPLOAD_BYTES).also(Random::nextBytes)
        }
        var uploaded = 0L
        var uploadTime = 0L
        repeat(UPLOAD_SAMPLES) {
            val url = server.uploadUrl.newBuilder()
                .addQueryParameter("cors", "true")
                .addQueryParameter("r", Random.nextLong().toString())
                .build()
            val started = System.nanoTime()
            val request = Request.Builder()
                .url(url)
                .post(uploadPayload.toRequestBody(OCTET_STREAM))
                .build()
            execute(request) { response ->
                require(response.isSuccessful) { "Upload request failed (${response.code})" }
            }
            uploaded += uploadPayload.size
            uploadTime += elapsedMillis(started)
        }

        val sortedPings = pings.sorted()
        val medianPing = sortedPings[sortedPings.size / 2]
        val jitter = pings.asList().zipWithNext { first, second -> kotlin.math.abs(second - first) }
            .average()
            .toLong()
        return NetworkSpeedTestResult(
            serverName = server.name,
            pingMs = medianPing,
            jitterMs = jitter,
            downloadMbps = megabitsPerSecond(downloaded, downloadTime),
            uploadMbps = megabitsPerSecond(uploaded, uploadTime),
        )
    }

    companion object {
        private const val SERVER_LIST_URL = "https://librespeed.org/backend-servers/servers.php"
        private const val MAX_SERVER_LIST_BYTES = 512L * 1024L
        private const val PING_SAMPLES = 5
        private const val DOWNLOAD_SAMPLES = 3
        private const val DOWNLOAD_MEGABYTES = 2
        private const val UPLOAD_SAMPLES = 2
        private const val UPLOAD_BYTES = 1_048_576
        private const val TRANSFER_BUFFER_BYTES = 8_192L
        private const val MAX_DOWNLOAD_BYTES_PER_SAMPLE = 8L * 1024 * 1024
        private const val MAX_SERVER_NAME_LENGTH = 120
        private const val MAX_URL_LENGTH = 2_048
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
        private val json = de.mm20.launcher2.serialization.Json.Lenient

        internal fun parsePublicServers(payload: String): List<LibreSpeedServer> {
            val root = json.parseToJsonElement(payload)
            val items = when (root) {
                is JsonArray -> root
                is JsonObject -> root["servers"] as? JsonArray ?: return emptyList()
                else -> return emptyList()
            }
            return items.mapNotNull { element ->
                runCatching {
                    val dto = json.decodeFromJsonElement<ServerDto>(element)
                    val ping = secureUrl(dto.pingUrl)
                    val download = secureUrl(dto.downloadUrl)
                    val upload = secureUrl(dto.uploadUrl)
                    if (ping == null || download == null || upload == null) return@runCatching null
                    val name = dto.name?.trim()?.takeIf(String::isNotBlank)?.take(MAX_SERVER_NAME_LENGTH)
                        ?: dto.sponsor?.trim()?.takeIf(String::isNotBlank)?.take(MAX_SERVER_NAME_LENGTH)
                        ?: secureUrl(dto.server)?.host
                        ?: return@runCatching null
                    LibreSpeedServer(name, ping, download, upload)
                }.getOrNull()
            }.distinctBy { it.pingUrl.host }.take(MAX_PUBLIC_SERVERS)
        }

        fun customServer(rawUrl: String): LibreSpeedServer? {
            val base = secureUrl(rawUrl.trim()) ?: return null
            if (base.query != null || base.fragment != null) return null
            fun endpoint(name: String): HttpUrl = base.newBuilder()
                    .encodedPath(base.encodedPath.trimEnd('/') + "/")
                    .addPathSegment(name)
                    .build()
            return LibreSpeedServer(
                name = base.host,
                pingUrl = endpoint("empty.php"),
                downloadUrl = endpoint("garbage.php"),
                uploadUrl = endpoint("empty.php"),
            )
        }

        internal fun megabitsPerSecond(bytes: Long, elapsedMillis: Long): Double {
            if (bytes <= 0L || elapsedMillis <= 0L) return 0.0
            return bytes * 8.0 / elapsedMillis / 1_000.0
        }

        internal fun readBoundedBytes(source: BufferedSource, maxBytes: Long): Long {
            require(maxBytes >= 0L)
            val buffer = Buffer()
            var count = 0L
            while (true) {
                val read = source.read(buffer, minOf(TRANSFER_BUFFER_BYTES, maxBytes - count + 1))
                if (read == -1L) return count
                count += read
                require(count <= maxBytes) { "Download response exceeded the size limit" }
                buffer.clear()
            }
        }

        private fun secureUrl(raw: String?): HttpUrl? {
            if (raw.isNullOrBlank() || raw.length > MAX_URL_LENGTH) return null
            val url = raw.toHttpUrlOrNull() ?: return null
            return url.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
        }

        private fun elapsedMillis(startedNanos: Long): Long =
            ((System.nanoTime() - startedNanos) / 1_000_000L).coerceAtLeast(1L)
    }
}

@Serializable
private data class ServerDto(
    val name: String? = null,
    val sponsor: String? = null,
    val server: String? = null,
    @SerialName("pingURL") val pingUrl: String? = null,
    @SerialName("dlURL") val downloadUrl: String? = null,
    @SerialName("ulURL") val uploadUrl: String? = null,
)

private const val MAX_PUBLIC_SERVERS = 100
