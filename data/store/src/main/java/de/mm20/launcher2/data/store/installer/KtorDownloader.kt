package de.mm20.launcher2.data.store.installer

import android.content.Context
import android.os.SystemClock
import de.mm20.launcher2.store.installer.*
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Stream APKs with validated HTTP Range resumption; partial files survive cancellation/restart. */
class KtorDownloader(
    private val context: Context,
    private val httpClient: HttpClient = HttpClient(),
) : Downloader {
    private val states = MutableStateFlow<Map<String, StoreDownloadProgress>>(emptyMap())
    override val progress: StateFlow<Map<String, StoreDownloadProgress>> = states
    private val paused = ConcurrentHashMap.newKeySet<String>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    override fun pause(key: String) {
        paused.add(key)
        states.update { all -> all[key]?.let { all + (key to it.copy(status = StoreDownloadStatus.Paused, bytesPerSecond = 0)) } ?: all }
    }
    override fun resume(key: String) { paused.remove(key) }
    private fun publish(key: String, value: StoreDownloadProgress) { states.update { it + (key to value) } }

    override suspend fun download(url: String, suggestedFileName: String, key: String): DownloadResult =
        locks.getOrPut(key) { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "store_downloads").apply { mkdirs() }
                val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
                val partial = File(dir, "$hash.part")
                val metadata = File(dir, "$hash.json")
                val destination = File(dir, "$hash-${File(suggestedFileName).name.ifBlank { "download.apk" }}")
                var retry = 0
                var total: Long? = null
                paused.remove(key)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    while (key in paused) {
                        publish(key, StoreDownloadProgress(partial.length(), total, status = StoreDownloadStatus.Paused))
                        delay(200)
                    }
                    val meta = runCatching { JSONObject(metadata.readText()) }.getOrDefault(JSONObject())
                    val validator = meta.optString("validator")
                    // An unvalidated object may have changed since the interrupted request.
                    if (partial.length() > 0 && validator.isEmpty()) partial.delete()
                    var offset = partial.length()
                    publish(key, StoreDownloadProgress(offset, total))
                    try {
                        val result = httpClient.prepareGet(url) {
                            header("Accept-Encoding", "identity")
                            if (offset > 0) {
                                header("Range", "bytes=$offset-")
                                header("If-Range", validator)
                            }
                        }.execute { response ->
                            val code = response.status.value
                            if (code == 416) {
                                partial.delete(); metadata.delete()
                                throw IOException("The remote file changed")
                            }
                            if (code == 408 || code == 429 || code >= 500) throw IOException("Server temporarily unavailable")
                            val append = StoreDownloadRange.appendOffset(code, response.headers["Content-Range"], offset)
                                ?: return@execute DownloadResult.Failed(context.getString(de.mm20.launcher2.i18n.R.string.telos_store_download_failed))
                            offset = append
                            total = response.headers["Content-Range"]?.substringAfterLast('/')?.toLongOrNull()
                                ?: response.headers["Content-Length"]?.toLongOrNull()?.let { offset + it }
                            val etag = response.headers["ETag"]?.takeUnless { it.startsWith("W/") }
                            metadata.writeText(JSONObject().put("validator", etag ?: response.headers["Last-Modified"].orEmpty()).toString())
                            var bytes = offset
                            var sampledBytes = bytes
                            var sampledAt = SystemClock.elapsedRealtime()
                            val channel = response.bodyAsChannel()
                            try {
                                FileOutputStream(partial, offset > 0).use { out ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        if (key in paused) throw PauseTransfer()
                                        val read = withTimeout(30_000) { channel.readAvailable(buffer, 0, buffer.size) }
                                        if (read < 0) break
                                        if (read == 0) continue
                                        out.write(buffer, 0, read)
                                        bytes += read
                                        val now = SystemClock.elapsedRealtime()
                                        if (now - sampledAt >= 500) {
                                            publish(key, StoreDownloadProgress(bytes, total, (bytes - sampledBytes) * 1000 / (now - sampledAt)))
                                            sampledBytes = bytes; sampledAt = now; retry = 0
                                        }
                                    }
                                }
                            } finally { channel.cancel() }
                            if (total != null && bytes != total) throw IOException("Download interrupted")
                            if (bytes == 0L) return@execute DownloadResult.Failed(context.getString(de.mm20.launcher2.i18n.R.string.telos_store_download_failed))
                            if (!partial.renameTo(destination)) throw IOException("Could not save the APK")
                            metadata.delete()
                            DownloadResult.Success(destination)
                        }
                        publish(key, StoreDownloadProgress(destination.length().takeIf { result is DownloadResult.Success } ?: partial.length(), total,
                            status = if (result is DownloadResult.Success) StoreDownloadStatus.Complete else StoreDownloadStatus.Failed))
                        return@withContext result
                    } catch (_: PauseTransfer) {
                        // Close the network request while paused; next request resumes from disk.
                    } catch (e: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        publish(key, StoreDownloadProgress(partial.length(), total, status = StoreDownloadStatus.WaitingForNetwork))
                        delay(2000)
                    } catch (e: CancellationException) {
                        publish(key, StoreDownloadProgress(partial.length(), total, status = StoreDownloadStatus.Paused))
                        throw e
                    } catch (e: IOException) {
                        publish(key, StoreDownloadProgress(partial.length(), total, status = StoreDownloadStatus.WaitingForNetwork))
                        delay((2000L shl retry.coerceAtMost(4)).coerceAtMost(30_000))
                        retry++
                    } catch (e: Exception) {
                        publish(key, StoreDownloadProgress(partial.length(), total, status = StoreDownloadStatus.Failed))
                        return@withContext DownloadResult.Failed(context.getString(de.mm20.launcher2.i18n.R.string.telos_store_download_failed), e)
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                DownloadResult.Failed("Download stopped")
            }
        }
    private class PauseTransfer : IOException()
}
