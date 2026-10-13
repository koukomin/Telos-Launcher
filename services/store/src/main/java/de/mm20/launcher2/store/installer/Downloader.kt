package de.mm20.launcher2.store.installer

import java.io.File
import kotlinx.coroutines.flow.StateFlow

sealed interface DownloadResult {
    data class Success(val file: File) : DownloadResult
    data class Failed(val reason: String, val cause: Throwable? = null) : DownloadResult
}

enum class StoreDownloadStatus { Downloading, Paused, WaitingForNetwork, Complete, Failed }
data class StoreDownloadProgress(
    val bytes: Long = 0,
    val total: Long? = null,
    val bytesPerSecond: Long = 0,
    val status: StoreDownloadStatus = StoreDownloadStatus.Downloading,
)

/** Persistent partial APKs, with one controllable transfer per Store item. */
interface Downloader {
    val progress: StateFlow<Map<String, StoreDownloadProgress>>
    fun pause(key: String)
    fun resume(key: String)
    suspend fun download(url: String, suggestedFileName: String, key: String = suggestedFileName): DownloadResult
}

/** Append only a validated partial response that starts at the byte requested. */
object StoreDownloadRange {
    fun appendOffset(code: Int, contentRange: String?, offset: Long): Long? {
        if (code == 200) return 0
        if (code != 206) return null
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(contentRange.orEmpty()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull()
        return start.takeIf { it == offset && end >= start && (total == null || end < total) }
    }
}
