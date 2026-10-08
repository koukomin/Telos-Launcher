package de.mm20.launcher2.downloads.engine

import de.mm20.launcher2.downloads.DownloadSettingsValues
import de.mm20.launcher2.downloads.DownloadTask
import de.mm20.launcher2.downloads.DownloadFiles
import de.mm20.launcher2.downloads.SegmentState

/**
 * Runs one kind of download. Phase 1 has [HttpDownloadEngine]; the torrent engine (phase 2) and the
 * media engine (phase 3) implement the same interface and are added to the list of engines of the
 * [de.mm20.launcher2.downloads.DownloadManager] in Module.kt.
 *
 * [execute] returns when the download is complete (the file is written and verified), throws a
 * [DownloadException] when it failed, and is cancelled by cancelling its coroutine (pause, cancel, network lost).
 * It must keep the progress in the session up to date and leave resumable state in the task.
 */
interface DownloadEngine {
    fun supports(task: DownloadTask): Boolean

    suspend fun execute(task: DownloadTask, session: EngineSession)
}

/** What the manager offers to an engine while it runs a task */
interface EngineSession {
    val settings: DownloadSettingsValues

    /** Limits all running downloads together */
    val globalLimiter: RateLimiter

    val files: DownloadFiles

    /** Changes the stored task (keeps resumable data, state changes, names). Always returns the new task. */
    suspend fun update(transform: (DownloadTask) -> DownloadTask): DownloadTask

    /** Live progress; cheap, called a few times per second. [segments] are stored with the next save. */
    fun progress(downloadedBytes: Long, speedBps: Long, segments: List<SegmentState>)
}
