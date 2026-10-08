package de.mm20.launcher2.downloads.media

/** The exit code and the output of one yt-dlp run */
class YtDlpResult(val exitCode: Int, val out: String, val err: String)

/**
 * How Telos runs yt-dlp. The only implementation is in the optional source set `mediaOn` (youtubedl-android,
 * included with -Ptelos.media=true); the default build has none and [YtDlpBackendFactory.create] returns null.
 */
interface YtDlpBackend {
    /** Unpacks Python, yt-dlp and FFmpeg on first use; throws when that fails */
    fun init()

    /** The installed yt-dlp version, null when unknown */
    fun version(): String?

    /** Downloads the newest yt-dlp from GitHub; returns a short status text */
    fun update(): String

    /** Blocks until yt-dlp ends. [onLine] gets every output line. Cancelled with [cancel]. */
    fun run(args: List<String>, processId: String, onLine: (String) -> Unit): YtDlpResult

    fun cancel(processId: String)
}
