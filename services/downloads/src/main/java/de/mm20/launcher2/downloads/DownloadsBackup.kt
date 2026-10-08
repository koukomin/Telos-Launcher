package de.mm20.launcher2.downloads

import de.mm20.launcher2.backup.BackupGroup
import de.mm20.launcher2.backup.Backupable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The download list (tasks.json) and the settings of Telos Downloads. Not in the backup: downloaded files and partial
 * data, cookies (the cookies.txt and the cookie of a task) and Authorization headers, the default folder (its
 * permission would not exist after a restore), and yt-dlp itself.
 *
 * Restored tasks never claim data that is not there: completed ones whose file is gone are marked [DownloadTask.fileMissing],
 * unfinished ones come back paused and start from the beginning when resumed.
 */
class DownloadsBackup(
    private val store: DownloadStore,
    private val settings: DownloadSettings,
    private val files: DownloadFiles,
) : Backupable {
    override val group: BackupGroup = BackupGroup.Downloads

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    override suspend fun backup(toDir: File) = withContext(Dispatchers.IO) {
        val dir = File(toDir, "downloads").also { it.mkdirs() }
        store.saveNow()
        File(dir, "tasks.json").writeText(json.encodeToString(store.tasks.value.map { sanitizeForBackup(it) }))
        File(dir, "settings.json").writeText(settings.exportJson())
    }

    override suspend fun restore(fromDir: File) = withContext(Dispatchers.IO) {
        val dir = File(fromDir, "downloads")
        File(dir, "settings.json").takeIf { it.exists() }?.let { runCatching { settings.importJson(it.readText()) } }
        val list = File(dir, "tasks.json").takeIf { it.exists() }?.let {
            runCatching { json.decodeFromString<List<DownloadTask>>(it.readText()) }.getOrNull()
        } ?: return@withContext
        val known = store.tasks.value.map { it.id }.toSet()
        for (t in list) {
            if (t.id in known) continue
            store.add(restoreTask(t) { uri -> files.exists(uri) })
        }
        store.saveNow()
    }

    companion object {
        /** What is written to a backup: no secrets, no transient state */
        fun sanitizeForBackup(t: DownloadTask): DownloadTask = t.copy(
            cookies = null,
            headers = t.headers.filterKeys { !it.equals("Authorization", true) && !it.equals("Cookie", true) },
            speedBps = 0,
            torrent = t.torrent?.copy(stagingPath = null, inQueue = false, moving = false),
        )

        /** What a restored task looks like, [exists] tells whether a stored file is still there */
        fun restoreTask(t: DownloadTask, exists: (String) -> Boolean): DownloadTask {
            val uris = buildList {
                t.fileUri?.let { add(it) }
                t.media?.outputUris?.let { addAll(it) }
                t.torrent?.files?.filter { it.wanted }?.forEach { f -> f.uri?.let { add(it) } }
            }.distinct()
            if (t.state == DownloadState.Completed || t.state == DownloadState.Seeding) {
                val torrentIncomplete = t.torrent?.files?.filter { it.wanted }?.any { it.uri == null } == true
                val missing = uris.isEmpty() || torrentIncomplete || uris.any { !exists(it) }
                // a torrent that was seeding is a finished download now
                return t.copy(state = DownloadState.Completed, fileMissing = missing, speedBps = 0, extractState = "")
            }
            if (t.state == DownloadState.Failed) return t.copy(speedBps = 0)
            // unfinished: the partial data is not in the backup, so it starts again
            return t.copy(
                state = DownloadState.Paused, downloadedBytes = 0, speedBps = 0, fileUri = null, segments = emptyList(),
                etag = null, lastModified = null, retryCount = 0, nextRetryAt = 0, error = null, errorKind = ErrorKind.None,
                media = t.media?.copy(stage = MediaStage.None, outputUris = emptyList()),
                torrent = t.torrent?.copy(
                    files = t.torrent.files.map { it.copy(done = 0, uri = null) }, stagingPath = null, moved = false,
                    inQueue = false, moving = false, uploadedBytes = 0, receivedBytes = 0, seedingSeconds = 0, seeds = 0, peers = 0,
                ),
            )
        }
    }
}
