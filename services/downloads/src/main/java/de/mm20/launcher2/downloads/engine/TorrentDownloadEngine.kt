package de.mm20.launcher2.downloads.engine

import android.content.Context
import android.os.SystemClock
import de.mm20.launcher2.comms.media.video.torrent.TorrentSession
import de.mm20.launcher2.downloads.DownloadException
import de.mm20.launcher2.downloads.DownloadState
import de.mm20.launcher2.downloads.DownloadTask
import de.mm20.launcher2.downloads.DownloadType
import de.mm20.launcher2.downloads.DownloadFiles
import de.mm20.launcher2.downloads.ErrorKind
import de.mm20.launcher2.downloads.TorrentData
import de.mm20.launcher2.downloads.TorrentFile
import de.mm20.launcher2.downloads.logic.FileSelection
import de.mm20.launcher2.downloads.logic.MimeTypes
import de.mm20.launcher2.downloads.logic.SeedRules
import de.mm20.launcher2.downloads.logic.TorrentPaths
import de.mm20.launcher2.downloads.logic.TorrentSourceKind
import de.mm20.launcher2.downloads.logic.TorrentSources
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.Vectors
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.SaveResumeDataFailedAlert
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads and seeds torrents with libtorrent, in the one session Telos shares with the video streamer
 * ([TorrentSession]). libtorrent writes real files, which it can neither do into a Storage Access Framework
 * folder nor into the Downloads collection. So the data goes into a staging folder in the storage of the
 * app, and when the wanted files are complete they are copied to the chosen folder; seeding goes on from the
 * staging folder, which is deleted when seeding ends (or the task is removed).
 *
 * The job of a torrent task runs while the torrent downloads and while it seeds, and ends when the seeding
 * limit is reached, when it is paused or removed, or when it fails. Resume data is saved every half minute,
 * when the job ends and when the file list changes, so that a torrent survives the end of the process.
 */
class TorrentDownloadEngine(
    private val context: Context,
    private val controller: TorrentController,
) : DownloadEngine {

    override fun supports(task: DownloadTask) = task.type == DownloadType.Torrent

    override suspend fun execute(task: DownloadTask, session: EngineSession) {
        // an incomplete proxy must not send the torrent around it
        val st = session.settings
        if (st.proxyType != de.mm20.launcher2.downloads.ProxyType.None && (st.proxyHost.isBlank() || st.proxyPort !in 1..65535)) {
            throw DownloadException(ErrorKind.Validation, "The proxy settings are incomplete", false)
        }
        TorrentSession.setProxy(context, st.torrentProxy())
        val files = session.files
        val owner = "download:${task.id}"
        val meta = files.torrentMetaDir(task.id)
        val staging = files.torrentStagingDir(task.id)
        val resumePending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
        var listener: AlertListener? = null
        var handle: TorrentHandle? = null
        var sessionManager: org.libtorrent4j.SessionManager? = null
        var finishedNormally = false
        try {
            val remaining = task.torrent?.files?.let { FileSelection.remainingBytes(it) } ?: 0L
            if (remaining > 0 && files.stagingFreeBytes() < remaining + 8L * 1024 * 1024) {
                throw DownloadException(ErrorKind.Storage, "Not enough free space for the torrent", false)
            }
            val params = buildParams(task, meta, staging)
            val sm = withContext(Dispatchers.IO) { TorrentSession.acquire(context, owner) }
            sessionManager = sm
            val ec = error_code()
            val th = sm.swig().add_torrent(params.swig(), ec)
            if (ec.value() != 0) throw DownloadException(ErrorKind.Unknown, ec.message(), false)
            val h = TorrentHandle(th)
            handle = h
            val hash = h.infoHash().toHex().lowercase()
            listener = object : AlertListener {
                override fun types(): IntArray = intArrayOf(AlertType.SAVE_RESUME_DATA.swig(), AlertType.SAVE_RESUME_DATA_FAILED.swig())
                override fun alert(alert: Alert<*>) {
                    try {
                        when (alert) {
                            is SaveResumeDataAlert -> if (alert.handle().infoHash().toHex().lowercase() == hash) {
                                writeAtomic(File(meta, "resume.dat"), AddTorrentParams.writeResumeDataBuf(alert.params()))
                                resumePending.remove(hash)?.complete(Unit)
                            }
                            is SaveResumeDataFailedAlert -> if (alert.handle().infoHash().toHex().lowercase() == hash) {
                                resumePending.remove(hash)?.complete(Unit)
                            }
                            else -> {}
                        }
                    } catch (e: Throwable) {
                        resumePending.remove(hash)?.complete(Unit)
                    }
                }
            }
            sm.addListener(listener)
            controller.register(task.id, h)
            session.update { t ->
                t.copy(torrent = (t.torrent ?: TorrentData(magnet = t.url)).copy(infoHash = hash, stagingPath = staging.absolutePath))
            }
            finishedNormally = runLoop(task.id, h, session, meta, staging, hash, resumePending)
        } finally {
            withContext(NonCancellable) {
                val h = handle
                if (h != null && h.isValid) {
                    requestResume(h, task.id, meta, resumePending, 4000)
                }
                controller.unregister(task.id)
                listener?.let { l -> runCatching { sessionManager?.removeListener(l) } }
                if (h != null) runCatching { if (h.isValid) sessionManager?.remove(h) }
                TorrentSession.release(owner)
                // the data was copied to its place and seeding is over: the staging copy is not needed any more
                val after = session.runCatchingUpdate { it }
                if (finishedNormally && after?.torrent?.let { d -> d.files.filter { it.wanted }.all { it.uri != null } } == true) {
                    staging.deleteRecursively()
                    session.runCatchingUpdate { t -> t.copy(torrent = t.torrent?.copy(stagingPath = null, moved = true)) }
                }
            }
        }
    }

    private suspend fun EngineSession.runCatchingUpdate(f: (DownloadTask) -> DownloadTask): DownloadTask? =
        try { update(f) } catch (e: CancellationException) { null }

    /** @return true when the job ended because seeding is over (and not because of an error) */
    private suspend fun runLoop(
        id: String,
        h: TorrentHandle,
        session: EngineSession,
        meta: File,
        staging: File,
        hash: String,
        resumePending: ConcurrentHashMap<String, CompletableDeferred<Unit>>,
    ): Boolean {
        var lastTick = SystemClock.elapsedRealtime()
        var lastFileProgress = 0L
        var lastResume = lastTick
        var lastLimit = -1L
        var lastFallback = -1
        var first = true
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!first) delay(1000)
            first = false
            val now = SystemClock.elapsedRealtime()
            val dt = now - lastTick
            lastTick = now
            if (!h.isValid) throw DownloadException(ErrorKind.Unknown, "The torrent left the session", true)
            val st = h.status()
            val err = st.errorCode()
            if (err != null && err.isError) throw DownloadException(ErrorKind.Storage, err.message, false)

            val task = session.update { it }
            val data = task.torrent ?: TorrentData(magnet = task.url)

            // limits: the per torrent one, and the global one for torrents when no torrent limit is set
            val fallback = session.settings.speedLimitKBps
            if (fallback != lastFallback) {
                lastFallback = fallback
                TorrentSession.setFallbackDownloadLimit(fallback)
            }
            val limit = task.speedLimitBps
            if (limit != lastLimit) {
                lastLimit = limit
                runCatching { h.downloadLimit = if (limit > 0) limit.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else -1 }
            }

            // the file list is known as soon as the metadata arrived (magnet links)
            var fileList = data.files
            var name = task.name
            var extra: (TorrentData) -> TorrentData = { it }
            if (fileList.isEmpty() && st.hasMetadata()) {
                val ti = h.torrentFile()
                if (ti != null) {
                    val fs = ti.files()
                    val prios = h.filePriorities()
                    val list = ArrayList<TorrentFile>()
                    for (i in 0 until ti.numFiles()) {
                        if (fs.padFileAt(i)) continue
                        list.add(TorrentFile(i, fs.filePath(i).replace('\\', '/'), fs.fileSize(i), priority = prios.getOrNull(i)?.swig()?.toInt() ?: FileSelection.NORMAL))
                    }
                    fileList = list
                    if (name.isBlank()) name = ti.name()
                    val root = TorrentPaths.rootFolder(list.map { it.path })
                    extra = { d -> d.copy(pieceLength = ti.pieceLength(), numPieces = ti.numPieces(), isPrivate = ti.isPrivate, rootFolder = root) }
                    requestResume(h, id, meta, resumePending, 0)
                }
            }
            if (fileList.isNotEmpty() && now - lastFileProgress >= 3000) {
                lastFileProgress = now
                val fp = runCatching { h.fileProgress() }.getOrNull()
                if (fp != null) fileList = fileList.map { f -> f.copy(done = fp.getOrNull(f.index) ?: f.done) }
            }

            val wanted = FileSelection.selectedBytes(fileList)
            val finished = st.hasMetadata() && st.isFinished && wanted > 0
            val queuedBySession = isQueued(st)
            var cur = session.update { it }
            var seedingSeconds = data.seedingSeconds

            if (finished) {
                // 100 %: copy to the folder, report, then seed
                val before = cur.torrent ?: data
                val updated = before.copy(files = fileList)
                if (!updated.files.filter { it.wanted }.all { it.uri != null }) {
                    cur = moveFiles(cur, updated, staging, session)
                    fileList = cur.torrent?.files ?: fileList
                    // the time spent copying is not seeding time
                    lastTick = SystemClock.elapsedRealtime()
                }
                if (cur.completedAt == 0L) session.markCompleted()
                seedingSeconds += dt / 1000
            }

            val rx = st.allTimeDownload()
            val tx = st.allTimeUpload()
            val ratio = SeedRules.ratio(tx, rx, wanted)
            val stop = finished && SeedRules.shouldStop(data.stopAtDone, data.seedRatioX100, data.seedMinutes, ratio, seedingSeconds)

            val newState = when {
                finished && !stop -> DownloadState.Seeding
                st.state() == TorrentStatus.State.CHECKING_FILES || st.state() == TorrentStatus.State.CHECKING_RESUME_DATA -> DownloadState.Verifying
                st.state() == TorrentStatus.State.DOWNLOADING_METADATA || queuedBySession -> DownloadState.Connecting
                else -> DownloadState.Downloading
            }
            val doneBytes = if (wanted > 0) st.totalWantedDone().coerceAtMost(wanted) else st.totalWantedDone()
            val down = st.downloadPayloadRate().toLong()
            val up = st.uploadPayloadRate().toLong()
            val seeds = st.numSeeds()
            val peers = st.numPeers()
            val mimeFiles = fileList.filter { it.wanted }
            session.update { t ->
                if (!t.state.isActive) t else {
                    val d = (t.torrent ?: data)
                    val single = mimeFiles.singleOrNull()
                    // priorities changed in the meantime (by the user) are kept, only the progress comes from here
                    val merged = d.files.map { old -> fileList.firstOrNull { it.index == old.index }?.let { old.copy(done = it.done, uri = old.uri ?: it.uri) } ?: old }
                        .ifEmpty { fileList }
                    t.copy(
                        state = newState,
                        name = name.ifBlank { t.name },
                        totalBytes = FileSelection.selectedBytes(merged).takeIf { it > 0 } ?: t.totalBytes,
                        downloadedBytes = doneBytes,
                        speedBps = down,
                        mimeType = t.mimeType ?: single?.let { MimeTypes.forName(it.path) },
                        torrent = extra(d).copy(
                            files = merged, uploadedBytes = tx, receivedBytes = rx, uploadBps = up, seeds = seeds, peers = peers,
                            seedingSeconds = seedingSeconds, inQueue = queuedBySession, moving = false,
                        ),
                    )
                }
            }

            if (stop) {
                requestResume(h, id, meta, resumePending, 4000)
                return true
            }
            if (now - lastResume >= 30_000) {
                lastResume = now
                if (h.needSaveResumeData()) requestResume(h, id, meta, resumePending, 0)
            }
        }
    }

    private fun isQueued(st: TorrentStatus): Boolean {
        val f = st.flags()
        val zero = torrent_flags_t()
        return f.and_(TorrentFlags.PAUSED).ne(zero) && f.and_(TorrentFlags.AUTO_MANAGED).ne(zero)
    }

    /** Copies the wanted files that are not yet in the chosen folder. @return the stored task */
    private suspend fun moveFiles(task: DownloadTask, data: TorrentData, staging: File, session: EngineSession): DownloadTask {
        val job = currentCoroutineContext()[Job]
        session.update { t -> if (t.state.isActive) t.copy(state = DownloadState.Verifying, torrent = (t.torrent ?: data).copy(moving = true)) else t }
        val folder = task.treeUri ?: session.settings.defaultFolder.ifBlank { null }
        val result = data.files.toMutableList()
        for ((i, f) in data.files.withIndex()) {
            if (!f.wanted) continue
            if (f.uri != null && session.files.exists(f.uri)) continue
            val src = File(staging, f.path)
            if (!src.exists() || src.length() < f.size) {
                throw DownloadException(ErrorKind.Storage, "A file is missing in the staging folder: ${f.path}", false)
            }
            val segments = TorrentPaths.safeSegments(f.path)
            val created = withContext(Dispatchers.IO) {
                session.files.copyTorrentFile(folder, segments, src, MimeTypes.forName(segments.last()), { job?.isActive == false }, {})
            }
            result[i] = f.copy(uri = created.uri, done = f.size)
            // remembered right away: a failure on a later file must not make the next try copy this one again
            session.update { t ->
                val d = t.torrent ?: return@update t
                t.copy(torrent = d.copy(files = d.files.map { if (it.index == f.index) it.copy(uri = created.uri) else it }))
            }
        }
        val wantedFiles = result.filter { it.wanted }
        val single = wantedFiles.singleOrNull()
        val biggest = wantedFiles.maxByOrNull { it.size }
        return session.update { t ->
            t.copy(
                fileUri = single?.uri,
                mimeType = single?.let { MimeTypes.forName(it.path) } ?: t.mimeType,
                category = t.category ?: biggest?.let { MimeTypes.categoryOf(it.path, MimeTypes.forName(it.path)) },
                torrent = (t.torrent ?: data).copy(files = result.mapIndexed { i, f -> f.copy(priority = t.torrent?.files?.getOrNull(i)?.priority ?: f.priority) }, moved = true, moving = false),
            )
        }
    }

    private suspend fun requestResume(
        h: TorrentHandle, id: String, meta: File, pending: ConcurrentHashMap<String, CompletableDeferred<Unit>>, waitMs: Long,
    ) {
        try {
            val key = h.infoHash().toHex().lowercase()
            val d = CompletableDeferred<Unit>()
            pending[key] = d
            h.saveResumeData(TorrentHandle.SAVE_INFO_DICT.or_(TorrentHandle.FLUSH_DISK_CACHE))
            if (waitMs > 0) withTimeoutOrNull(waitMs) { d.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // the next save will do
        }
    }

    private fun buildParams(task: DownloadTask, meta: File, staging: File): AddTorrentParams {
        val data = task.torrent ?: TorrentData(magnet = task.url)
        val cfg = TorrentSession.config.value
        val resume = File(meta, "resume.dat")
        val torrentFile = File(meta, "meta.torrent")
        var p: AddTorrentParams? = null
        if (resume.exists()) {
            val ec = error_code()
            val sp = libtorrent.read_resume_data_ex(Vectors.bytes2byte_vector(resume.readBytes()), ec)
            if (ec.value() == 0) p = AddTorrentParams(sp)
        }
        if (p == null && torrentFile.exists()) {
            p = AddTorrentParams().apply { torrentInfo = TorrentInfo.bdecode(torrentFile.readBytes()) }
        }
        if (p == null) {
            p = when (TorrentSources.classify(task.url)) {
                TorrentSourceKind.Magnet -> AddTorrentParams.parseMagnetUri(task.url)
                else -> {
                    // an address or file that was added without reading it first
                    val bytes = kotlinx.coroutines.runBlocking { TorrentMetadata.load(context, task.url).torrentBytes }
                    torrentFile.writeBytes(bytes)
                    AddTorrentParams().apply { torrentInfo = TorrentInfo.bdecode(bytes) }
                }
            }
        }
        p.savePath = staging.absolutePath
        var f = p.flags
        f = f.and_(TorrentFlags.PAUSED.inv()).and_(TorrentFlags.DUPLICATE_IS_ERROR.inv())
            .or_(TorrentFlags.AUTO_MANAGED).or_(TorrentFlags.APPLY_IP_FILTER)
        f = if (cfg.pex) f.and_(TorrentFlags.DISABLE_PEX.inv()) else f.or_(TorrentFlags.DISABLE_PEX)
        f = if (cfg.dht) f.and_(TorrentFlags.DISABLE_DHT.inv()) else f.or_(TorrentFlags.DISABLE_DHT)
        f = if (cfg.lsd) f.and_(TorrentFlags.DISABLE_LSD.inv()) else f.or_(TorrentFlags.DISABLE_LSD)
        f = if (data.sequential) f.or_(TorrentFlags.SEQUENTIAL_DOWNLOAD) else f.and_(TorrentFlags.SEQUENTIAL_DOWNLOAD.inv())
        p.flags = f
        if (data.files.isNotEmpty()) {
            val prios = FileSelection.priorities(data.files)
            p.filePriorities(Array(prios.size) { Priority.fromSwig(prios[it]) })
        }
        return p
    }

    override suspend fun cleanup(task: DownloadTask, deleteFiles: Boolean) {
        val files = DownloadFiles(context)
        withContext(Dispatchers.IO) {
            runCatching { files.torrentStagingDir(task.id).deleteRecursively() }
            runCatching { files.torrentMetaDir(task.id).deleteRecursively() }
            // finished files are only deleted when the user asks; what was never finished leaves nothing behind
            val data = task.torrent
            if (data != null && (deleteFiles || task.completedAt == 0L)) {
                for (f in data.files) f.uri?.let { files.delete(it) }
            }
        }
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }
}
