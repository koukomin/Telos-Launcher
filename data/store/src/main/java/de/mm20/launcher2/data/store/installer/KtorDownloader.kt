package de.mm20.launcher2.data.store.installer

import android.content.Context
import android.util.Log
import de.mm20.launcher2.store.installer.DownloadResult
import de.mm20.launcher2.store.installer.Downloader
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Streams a release APK straight from [url] to a file under the app's cache dir, without buffering
 * the whole download in memory - release APKs can run well past 100MB.
 */
class KtorDownloader(
    private val context: Context,
    private val httpClient: HttpClient = HttpClient(),
) : Downloader {

    override suspend fun download(url: String, suggestedFileName: String): DownloadResult =
        withContext(Dispatchers.IO) {
            val downloadsDir = File(context.cacheDir, "store_downloads").apply { mkdirs() }
            val destination = File(downloadsDir, File(suggestedFileName).name.ifBlank { "download.apk" })
            try {
                val response = httpClient.get { url(url) }
                if (!response.status.isSuccess()) {
                    return@withContext DownloadResult.Failed("Download failed with status ${response.status}")
                }
                destination.outputStream().use { out ->
                    response.bodyAsChannel().copyTo(out)
                }
                DownloadResult.Success(destination)
            } catch (e: Exception) {
                destination.delete()
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Download failed for $url", e)
                DownloadResult.Failed("Download failed: ${e.message}", e)
            }
        }

    companion object {
        private const val TAG = "KtorDownloader"
    }
}
