package de.mm20.launcher2.downloads.media

import android.content.Context
import de.mm20.launcher2.downloads.DownloadException
import de.mm20.launcher2.downloads.ErrorKind
import de.mm20.launcher2.downloads.logic.CookieFiles
import de.mm20.launcher2.downloads.logic.MediaFormats
import de.mm20.launcher2.downloads.logic.MediaInfo
import de.mm20.launcher2.downloads.logic.MediaInfoParser
import de.mm20.launcher2.downloads.logic.YtDlpErrors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Everything around yt-dlp that is not a download: whether the runtime is part of this build, the analysis of a
 * link, the cookies.txt, and the update of yt-dlp. The runtime is unpacked on first use (a few seconds).
 */
class MediaRuntime(private val context: Context) {
    private val prefs = context.getSharedPreferences("telos_downloads_media", Context.MODE_PRIVATE)

    /** null in builds without the runtime */
    val backend: YtDlpBackend? by lazy { YtDlpBackendFactory.create(context) }

    val isAvailable: Boolean get() = backend != null

    val cookiesFile: File get() = File(File(context.filesDir, "downloads/media").also { it.mkdirs() }, "cookies.txt")

    val hasCookies: Boolean get() = cookiesFile.let { it.exists() && it.length() > 0 }

    /** Saves a cookies.txt the user picked. @return the number of cookies, 0 (and nothing saved) when it is not a cookies.txt */
    fun importCookies(text: String): Int {
        val n = CookieFiles.countCookies(text)
        if (n > 0) cookiesFile.writeText(text)
        return n
    }

    /** Adds the cookies of a web view for [url] to the cookies.txt (other sites stay) */
    fun addCookieHeader(url: String, header: String): Int {
        val fresh = CookieFiles.fromHeader(url, header)
        val n = CookieFiles.countCookies(fresh)
        if (n == 0) return 0
        val old = if (hasCookies) cookiesFile.readText().lines().filter { it.isNotBlank() && !it.startsWith("# Netscape") } else emptyList()
        val keep = old.filter { l -> val d = l.split('\t').firstOrNull().orEmpty(); fresh.lines().none { it.startsWith(d + "\t") } }
        cookiesFile.writeText(CookieFiles.HEADER + "\n" + keep.joinToString("\n") + (if (keep.isEmpty()) "" else "\n") + fresh.lines().drop(1).joinToString("\n"))
        return n
    }

    fun clearCookies() {
        cookiesFile.delete()
    }

    /** Fetches title, thumbnail, duration, uploader, formats or the playlist entries. Blocking work runs on the IO dispatcher. */
    suspend fun analyze(url: String, useCookies: Boolean, proxy: String?): MediaInfo = withContext(Dispatchers.IO) {
        val b = backend ?: throw DownloadException(ErrorKind.Validation, "The video downloader is not part of this build", false)
        val cookies = if (useCookies && hasCookies) cookiesFile.absolutePath else null
        val pid = "analyze-${System.nanoTime()}"
        // the blocking run does not notice a cancelled coroutine: stop the process, or it would go on for a whole playlist
        val r = coroutineScope {
            val run = async(Dispatchers.IO) { b.run(MediaFormats.analyzeArgs(url, cookies, proxy), pid) {} }
            try {
                run.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                b.cancel(pid)
                throw e
            }
        }
        val info = MediaInfoParser.parse(r.out)
        if (info == null) {
            val text = r.err + "\n" + r.out
            val err = YtDlpErrors.classify(text)
            throw DownloadException(if (err.retryable) ErrorKind.Network else ErrorKind.Validation, YtDlpErrors.detail(text), err.retryable)
        }
        info
    }

    val version: String? get() = prefs.getString("version", null) ?: backend?.version()?.also { prefs.edit().putString("version", it).apply() }

    /** Epoch milliseconds of the last successful update, 0 when never */
    val lastUpdate: Long get() = prefs.getLong("lastUpdate", 0)

    /** Updates yt-dlp from GitHub. @return the status text, throws when it failed */
    suspend fun update(): String = withContext(Dispatchers.IO) {
        val b = backend ?: throw DownloadException(ErrorKind.Validation, "The video downloader is not part of this build", false)
        val status = b.update()
        val v = b.version()
        prefs.edit().putLong("lastUpdate", System.currentTimeMillis()).apply { if (v != null) putString("version", v) }.apply()
        status
    }
}
