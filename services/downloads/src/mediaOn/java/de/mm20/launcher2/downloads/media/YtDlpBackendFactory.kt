package de.mm20.launcher2.downloads.media

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest

/** Build with the yt-dlp runtime (-Ptelos.media=true): youtubedl-android with Python, yt-dlp and FFmpeg. */
object YtDlpBackendFactory {
    fun create(context: Context): YtDlpBackend? = YoutubeDlAndroidBackend(context.applicationContext)
}

private class YoutubeDlAndroidBackend(private val context: Context) : YtDlpBackend {
    @Volatile private var ready = false

    @Synchronized
    override fun init() {
        if (ready) return
        YoutubeDL.getInstance().init(context)
        FFmpeg.getInstance().init(context)
        ready = true
    }

    override fun version(): String? = try {
        init()
        YoutubeDL.version(context)
    } catch (e: Throwable) {
        null
    }

    override fun update(): String {
        init()
        val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        return status?.name ?: "DONE"
    }

    override fun run(args: List<String>, processId: String, onLine: (String) -> Unit): YtDlpResult {
        init()
        val request = YoutubeDLRequest(emptyList())
        request.addCommands(args)
        return try {
            val r = YoutubeDL.getInstance().execute(request, processId) { _, _, line -> onLine(line) }
            YtDlpResult(r.exitCode, r.out, r.err)
        } catch (e: YoutubeDL.CanceledException) {
            throw kotlinx.coroutines.CancellationException("yt-dlp was stopped")
        } catch (e: Exception) {
            YtDlpResult(1, "", e.message.orEmpty())
        }
    }

    override fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }
}
