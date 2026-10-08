package de.mm20.launcher2.downloads

import kotlinx.serialization.Serializable

/** Which engine runs a task: [Http] (phase 1), [Torrent] (phase 2); [Media] is reserved for phase 3. */
@Serializable
enum class DownloadType { Http, Torrent, Media }

@Serializable
enum class DownloadState {
    /** Waiting for a free slot, for the network, or for the next retry */
    Queued,
    Connecting,
    Downloading,
    Verifying,

    /** A finished torrent that is shared with other peers until its seeding limit is reached */
    Seeding,
    Paused,
    Completed,
    Failed;

    val isActive: Boolean get() = this == Connecting || this == Downloading || this == Verifying || this == Seeding
    val isFinished: Boolean get() = this == Completed || this == Failed
}

@Serializable
enum class DownloadCategory { Video, Audio, Documents, Archives, Programs, Other }

@Serializable
enum class ErrorKind { None, Network, Http, Storage, Validation, Checksum, Unknown }

/** One byte range of the file. [end] is inclusive and -1 when the size is unknown. */
@Serializable
data class SegmentState(
    val index: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long = 0,
) {
    val length: Long get() = if (end < 0) -1 else end - start + 1
    val isComplete: Boolean get() = end >= 0 && downloaded >= length
}

/** One file of a torrent. [priority] is 0 (skip) to 7; 4 is the normal one. */
@Serializable
data class TorrentFile(
    val index: Int,
    /** Path inside the torrent, with "/" */
    val path: String,
    val size: Long,
    val priority: Int = 4,
    val done: Long = 0,
    /** Where the finished file was put (content:// or file://), null until then */
    val uri: String? = null,
) {
    val wanted: Boolean get() = priority > 0
}

/** Everything that is only there for torrents */
@Serializable
data class TorrentData(
    val infoHash: String = "",
    val magnet: String = "",
    val files: List<TorrentFile> = emptyList(),
    val sequential: Boolean = false,
    /** Stop seeding at this ratio, in hundredths (100 = 1.0), 0: no ratio limit */
    val seedRatioX100: Int = 100,
    /** Stop seeding after this many minutes, 0: no time limit */
    val seedMinutes: Int = 0,
    val stopAtDone: Boolean = false,
    val uploadedBytes: Long = 0,
    /** Everything received, also what was thrown away; the ratio is uploaded divided by this */
    val receivedBytes: Long = 0,
    val uploadBps: Long = 0,
    val seeds: Int = 0,
    val peers: Int = 0,
    val seedingSeconds: Long = 0,
    val pieceLength: Int = 0,
    val numPieces: Int = 0,
    val isPrivate: Boolean = false,
    /** Folder of the torrent data inside the app storage (staging) */
    val stagingPath: String? = null,
    /** The finished files were copied to the chosen folder */
    val moved: Boolean = false,
    /** Name of the top folder of a multi-file torrent, blank for a single file */
    val rootFolder: String = "",
    /** Waiting in the queue of the torrent session (more torrents than "max active torrents") */
    val inQueue: Boolean = false,
    /** The finished files are being copied to the chosen folder */
    val moving: Boolean = false,
) {
    val ratio: Double get() = if (receivedBytes > 0) uploadedBytes.toDouble() / receivedBytes else 0.0
    val wantedBytes: Long get() = files.filter { it.wanted }.sumOf { it.size }
}

/**
 * One download. Everything the engines need to run or resume it is in here, so that a task can be
 * stored as plain JSON ([DownloadStore]) and restored after the process was killed.
 *
 * [url] is a http(s) address now; a magnet link for [DownloadType.Torrent] later.
 */
@Serializable
data class DownloadTask(
    val id: String,
    val type: DownloadType = DownloadType.Http,
    val url: String,
    val mirrors: List<String> = emptyList(),
    /** Blank until the engine knows the file name (Content-Disposition, URL) */
    val name: String = "",
    /** Folder chosen with the Storage Access Framework (tree uri), null: Downloads/Telos in the Downloads collection */
    val treeUri: String? = null,
    /** Where the file is written (content:// or file://), set when the file was created */
    val fileUri: String? = null,
    val state: DownloadState = DownloadState.Queued,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    /** Not meaningful after a restart */
    val speedBps: Long = 0,
    val error: String? = null,
    val errorKind: ErrorKind = ErrorKind.None,
    /** null: decided from the file name and type when it is known */
    val category: DownloadCategory? = null,
    val createdAt: Long = 0,
    val completedAt: Long = 0,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
    val referer: String? = null,
    val cookies: String? = null,
    /** 0: use the default of the settings */
    val connections: Int = 0,
    /** Bytes per second for this task, 0: no limit of its own */
    val speedLimitBps: Long = 0,
    /** "sha256:hex", "md5:hex", "sha1:hex" or plain hex (algorithm by length) */
    val checksum: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val acceptRanges: Boolean = false,
    /** Address after redirects */
    val resolvedUrl: String? = null,
    val mimeType: String? = null,
    val segments: List<SegmentState> = emptyList(),
    val retryCount: Int = 0,
    /** Time (epoch ms) before which a queued task is not started again */
    val nextRetryAt: Long = 0,
    /** Higher runs first */
    val priority: Int = 0,
    /** Set for [DownloadType.Torrent] */
    val torrent: TorrentData? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val displayName: String get() = name.ifBlank { url }

    /** Remaining time in seconds, null when it can not be told */
    val etaSeconds: Long?
        get() = if (state == DownloadState.Downloading && speedBps > 0 && totalBytes > 0) {
            ((totalBytes - downloadedBytes).coerceAtLeast(0) / speedBps)
        } else null
}

/** What the user (or a share target) asks for when adding a download */
data class DownloadRequest(
    val url: String,
    val type: DownloadType = DownloadType.Http,
    val name: String = "",
    val mirrors: List<String> = emptyList(),
    val treeUri: String? = null,
    val category: DownloadCategory? = null,
    val connections: Int = 0,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
    val referer: String? = null,
    val cookies: String? = null,
    val checksum: String? = null,
    val speedLimitBps: Long = 0,
    val startPaused: Boolean = false,
    /** Set for [DownloadType.Torrent]: what the user chose in the add sheet */
    val torrent: TorrentRequest? = null,
)

/** The choices made before a torrent is added; [torrentFile] is the metadata read in the add sheet (optional for magnet links) */
class TorrentRequest(
    val torrentFile: ByteArray? = null,
    val files: List<TorrentFile> = emptyList(),
    val sequential: Boolean = false,
    val seedRatioX100: Int = 100,
    val seedMinutes: Int = 0,
    val stopAtDone: Boolean = false,
    val infoHash: String = "",
    val pieceLength: Int = 0,
    val numPieces: Int = 0,
    val isPrivate: Boolean = false,
    val rootFolder: String = "",
)

/** Thrown by engines: [retryable] failures (network, server busy) are tried again with a backoff */
class DownloadException(
    val kind: ErrorKind,
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause)
