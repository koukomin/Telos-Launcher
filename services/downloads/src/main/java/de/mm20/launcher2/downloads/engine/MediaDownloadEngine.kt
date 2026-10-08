package de.mm20.launcher2.downloads.engine

import android.content.Context
import de.mm20.launcher2.downloads.DownloadCategory
import de.mm20.launcher2.downloads.DownloadException
import de.mm20.launcher2.downloads.DownloadFiles
import de.mm20.launcher2.downloads.DownloadState
import de.mm20.launcher2.downloads.DownloadTask
import de.mm20.launcher2.downloads.DownloadType
import de.mm20.launcher2.downloads.ErrorKind
import de.mm20.launcher2.downloads.MediaData
import de.mm20.launcher2.downloads.MediaStage
import de.mm20.launcher2.downloads.logic.MediaError
import de.mm20.launcher2.downloads.logic.MediaFiles
import de.mm20.launcher2.downloads.logic.MediaFormats
import de.mm20.launcher2.downloads.logic.MediaProgressTracker
import de.mm20.launcher2.downloads.logic.MimeTypes
import de.mm20.launcher2.downloads.logic.YtDlpErrors
import de.mm20.launcher2.downloads.media.MediaRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import de.mm20.launcher2.i18n.R as I18nR

/**
 * Downloads video and audio from web sites with yt-dlp (youtubedl-android, see [MediaRuntime]). yt-dlp writes real
 * files, so it works in a staging folder in the app storage; when it is done the results (video, subtitles) are
 * copied to the chosen folder like the files of a torrent. Pausing kills the process and keeps the `.part` files,
 * yt-dlp continues from them on resume. One task is one video; the add sheet makes one task per playlist item.
 */
class MediaDownloadEngine(
    private val context: Context,
    private val runtime: MediaRuntime,
) : DownloadEngine {

    override fun supports(task: DownloadTask) = task.type == DownloadType.Media

    override suspend fun execute(task: DownloadTask, session: EngineSession) {
        val backend = runtime.backend ?: throw DownloadException(ErrorKind.Validation, context.getString(I18nR.string.dl_m_err_notinstalled), false)
        val data = task.media ?: MediaData()
        val staging = session.files.mediaStagingDir(task.id)
        val pid = "telos-media-${task.id}"
        val settings = session.settings

        session.update { t -> if (t.state.isActive) t.copy(media = (t.media ?: data).copy(stage = MediaStage.Starting)) else t }

        val cookies = if (data.useCookies && runtime.hasCookies) {
            File(staging, "cookies.txt").also { runtime.cookiesFile.copyTo(it, overwrite = true) }.absolutePath
        } else null
        val limit = when {
            task.speedLimitBps > 0 -> task.speedLimitBps
            settings.speedLimitKBps > 0 -> settings.speedLimitKBps * 1024L
            else -> 0L
        }
        val proxy = settings.proxyUrl
        val env = MediaFormats.Env(
            stagingDir = staging.absolutePath, cookiesFile = cookies, rateLimitBps = limit, proxy = proxy,
            userAgent = task.userAgent ?: settings.userAgent.takeIf { it.isNotBlank() }, referer = task.referer, headers = task.headers,
        )
        val args = MediaFormats.downloadArgs(task.url, data, env)
        val tracker = MediaProgressTracker(data.expectedBytes)
        val tail = ArrayDeque<String>()
        val latest = AtomicReference<MediaProgressTracker.Snapshot?>(null)

        val result = coroutineScope {
            val ticker = launch {
                var shown: MediaProgressTracker.Snapshot? = null
                while (isActive) {
                    delay(700)
                    val s = latest.get()
                    if (s != null && s !== shown) {
                        shown = s
                        session.update { t ->
                            if (!t.state.isActive) t else t.copy(
                                state = DownloadState.Downloading,
                                downloadedBytes = s.downloaded,
                                totalBytes = if (s.total > 0) s.total else t.totalBytes,
                                speedBps = s.speedBps,
                                media = (t.media ?: data).copy(stage = s.stage),
                            )
                        }
                    }
                }
            }
            val run = async(Dispatchers.IO) {
                backend.run(args, pid) { line ->
                    synchronized(tail) { tail.addLast(line); if (tail.size > 40) tail.removeFirst() }
                    tracker.feed(line)?.let { latest.set(it) }
                }
            }
            try {
                run.await()
            } catch (e: CancellationException) {
                // pause or remove: kill the process, the .part files stay for the next run
                backend.cancel(pid)
                throw e
            } finally {
                ticker.cancel()
            }
        }

        if (result.exitCode != 0) {
            val text = result.err + "\n" + result.out + "\n" + synchronized(tail) { tail.joinToString("\n") }
            val kind = YtDlpErrors.classify(text)
            val message = context.getString(messageFor(kind)) + YtDlpErrors.detail(text).takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
            val errorKind = when (kind) {
                MediaError.Network, MediaError.RateLimited -> ErrorKind.Network
                MediaError.DiskFull -> ErrorKind.Storage
                MediaError.Forbidden -> ErrorKind.Http
                else -> ErrorKind.Unknown
            }
            throw DownloadException(errorKind, message, kind.retryable)
        }

        copyResults(task, data, staging, session)
    }

    private suspend fun copyResults(task: DownloadTask, data: MediaData, staging: File, session: EngineSession) {
        session.update { t -> if (t.state.isActive) t.copy(state = DownloadState.Verifying, speedBps = 0, media = (t.media ?: data).copy(stage = MediaStage.Copying)) else t }
        val names = MediaFiles.finishedOutputs(staging.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty())
        if (names.isEmpty()) throw DownloadException(ErrorKind.Validation, context.getString(I18nR.string.dl_m_err_nofile), false)
        val main = MediaFiles.mainFile(names.map { it to File(staging, it).length() }) ?: names.first()
        val folder = task.treeUri ?: session.settings.defaultFolder.ifBlank { null }
        val job = currentCoroutineContext()[kotlinx.coroutines.Job]
        val uris = ArrayList<String>()
        var mainUri: String? = null
        var mainName = main
        try {
            for (n in names) {
                val src = File(staging, n)
                val created = withContext(Dispatchers.IO) {
                    session.files.copyTorrentFile(folder, listOf(n), src, MimeTypes.forName(n), { job?.isActive == false }, {})
                }
                uris += created.uri
                if (n == main) { mainUri = created.uri; mainName = created.name }
            }
        } catch (e: Exception) {
            // nothing half copied stays behind; the staging files stay for the next try
            for (u in uris) session.files.delete(u)
            throw e
        }
        val size = File(staging, main).length()
        val mime = MimeTypes.forName(main)
        withContext(NonCancellable) {
            session.update { t ->
                t.copy(
                    name = mainName, fileUri = mainUri, totalBytes = size, downloadedBytes = size, mimeType = mime,
                    category = if (data.audioOnly) DownloadCategory.Audio else MimeTypes.categoryOf(main, mime),
                    media = (t.media ?: data).copy(outputUris = uris, stage = MediaStage.None),
                )
            }
            staging.deleteRecursively()
        }
    }

    private fun messageFor(e: MediaError): Int = when (e) {
        MediaError.Unsupported -> I18nR.string.dl_m_err_unsupported
        MediaError.LoginRequired -> I18nR.string.dl_m_err_login
        MediaError.Private -> I18nR.string.dl_m_err_private
        MediaError.GeoBlocked -> I18nR.string.dl_m_err_geo
        MediaError.Unavailable -> I18nR.string.dl_m_err_unavailable
        MediaError.RateLimited -> I18nR.string.dl_m_err_ratelimit
        MediaError.Forbidden -> I18nR.string.dl_m_err_forbidden
        MediaError.Network -> I18nR.string.dl_m_err_network
        MediaError.Ffmpeg -> I18nR.string.dl_m_err_ffmpeg
        MediaError.DiskFull -> I18nR.string.dl_m_err_disk
        MediaError.FormatUnavailable -> I18nR.string.dl_m_err_format
        MediaError.NotInstalled -> I18nR.string.dl_m_err_notinstalled
        MediaError.Other -> I18nR.string.dl_m_err_other
    }

    override suspend fun cleanup(task: DownloadTask, deleteFiles: Boolean) {
        val files = DownloadFiles(context)
        withContext(Dispatchers.IO) {
            runCatching { files.mediaStagingDir(task.id).deleteRecursively() }
            if (deleteFiles || task.completedAt == 0L) {
                task.media?.outputUris?.forEach { files.delete(it) }
                task.fileUri?.let { files.delete(it) }
            }
        }
    }
}
