package de.mm20.launcher2.downloads

import android.content.Context
import de.mm20.launcher2.downloads.engine.DownloadEngine
import de.mm20.launcher2.downloads.engine.EngineSession
import de.mm20.launcher2.downloads.engine.RateLimiter
import de.mm20.launcher2.downloads.logic.BlockReason
import de.mm20.launcher2.downloads.logic.QueueRules
import de.mm20.launcher2.downloads.logic.RetryPolicy
import de.mm20.launcher2.downloads.logic.SegmentPlanner
import de.mm20.launcher2.downloads.logic.TorrentSourceKind
import de.mm20.launcher2.downloads.logic.TorrentSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

sealed interface DownloadEvent {
    val task: DownloadTask

    data class Completed(override val task: DownloadTask) : DownloadEvent
    data class Failed(override val task: DownloadTask) : DownloadEvent
}

/**
 * The scheduler and the API of Telos Downloads: keeps the tasks in the [DownloadStore], starts as many
 * as the queue rules allow ([QueueRules]), hands them to the first [DownloadEngine] that supports
 * them, retries with backoff, waits for the network / Wi-Fi / battery, and keeps the
 * [DownloadService] running while there is work.
 */
class DownloadManager(
    private val context: Context,
    private val store: DownloadStore,
    val settings: DownloadSettings,
    val files: DownloadFiles,
    private val engines: List<DownloadEngine>,
    private val notifier: DownloadNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()

    /** What a stopped job should leave behind as its state (set by the one that stops it) */
    private val stopTargets = ConcurrentHashMap<String, DownloadState>()
    private val globalLimiter = RateLimiter()
    private val monitor = ConditionsMonitor(context)
    private val kicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var started = false
    private var wakeAt = 0L
    private var lastServiceStart = 0L
    private var wakeJob: Job? = null

    val tasks: StateFlow<List<DownloadTask>> = store.tasks
    val conditions: StateFlow<de.mm20.launcher2.downloads.logic.Conditions> = monitor.conditions

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    /** The reason why nothing runs, null when downloads may run */
    private val _blockReason = MutableStateFlow<BlockReason?>(null)
    val blockReason: StateFlow<BlockReason?> = _blockReason

    /** True while the foreground service should run; the service stops itself when this turns false */
    private val _serviceWanted = MutableStateFlow(false)
    val serviceWanted: StateFlow<Boolean> = _serviceWanted

    fun supportsType(type: DownloadType): Boolean = engines.any { e ->
        e.supports(DownloadTask(id = "", type = type, url = if (type == DownloadType.Http) "https://x" else ""))
    }

    /** Call once when the app starts: tasks that were running when the process died are queued again. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        val interrupted = store.tasks.value.filter { it.state.isActive }
        for (t in interrupted) store.update(t.id) { it.copy(state = DownloadState.Queued, speedBps = 0) }
        // an extraction that was running when the process died is not running any more
        for (t in store.tasks.value.filter { it.extractState == "running" }) store.update(t.id) { it.copy(extractState = "") }
        if (store.tasks.value.any { !it.state.isFinished }) monitor.start()
        scope.launch {
            merge(
                store.tasks.map { }, settings.values.map { }, monitor.conditions.map { }, kicks,
            ).collect { schedule() }
        }
    }

    // ---- API

    fun add(request: DownloadRequest): DownloadTask {
        start()
        monitor.start()
        val url = request.url.trim()
        val type = if (request.type == DownloadType.Http && TorrentSources.classify(url) == TorrentSourceKind.Magnet) DownloadType.Torrent else request.type
        val id = UUID.randomUUID().toString()
        var torrentData: TorrentData? = null
        var torrentName = request.name.trim()
        if (type == DownloadType.Torrent) {
            val t = request.torrent ?: TorrentRequest()
            val hash = t.infoHash.ifBlank { TorrentSources.magnetInfoHash(url).orEmpty() }
            if (hash.isNotEmpty()) {
                // the same torrent twice would drive one libtorrent torrent from two tasks
                store.tasks.value.firstOrNull { it.torrent?.infoHash.equals(hash, ignoreCase = true) }?.let { return it }
            }
            if (t.torrentFile != null) {
                runCatching { java.io.File(files.torrentMetaDir(id), "meta.torrent").writeBytes(t.torrentFile) }
            }
            torrentData = TorrentData(
                infoHash = hash.lowercase(), magnet = if (TorrentSources.classify(url) == TorrentSourceKind.Magnet) url else "",
                files = t.files, sequential = t.sequential, seedRatioX100 = t.seedRatioX100, seedMinutes = t.seedMinutes,
                stopAtDone = t.stopAtDone, pieceLength = t.pieceLength, numPieces = t.numPieces, isPrivate = t.isPrivate,
                rootFolder = t.rootFolder,
            )
            if (torrentName.isEmpty()) torrentName = TorrentSources.magnetName(url).orEmpty()
        }
        val task = DownloadTask(
            id = id,
            type = type,
            torrent = torrentData,
            totalBytes = torrentData?.takeIf { it.files.isNotEmpty() }?.wantedBytes ?: -1,
            url = url,
            mirrors = request.mirrors.map { it.trim() }.filter { it.isNotEmpty() },
            name = torrentName,
            treeUri = request.treeUri,
            state = if (request.startPaused) DownloadState.Paused else DownloadState.Queued,
            category = request.category,
            createdAt = System.currentTimeMillis(),
            headers = request.headers,
            userAgent = request.userAgent?.takeIf { it.isNotBlank() },
            referer = request.referer?.takeIf { it.isNotBlank() },
            cookies = request.cookies?.takeIf { it.isNotBlank() },
            connections = if (request.connections > 0) SegmentPlanner.clampConnections(request.connections) else 0,
            speedLimitBps = request.speedLimitBps.coerceAtLeast(0),
            checksum = request.checksum?.takeIf { it.isNotBlank() },
            media = if (type == DownloadType.Media) request.media ?: MediaData() else null,
        ).let { t ->
            if (t.media == null) t else t.copy(
                name = t.name.ifBlank { t.media.title },
                totalBytes = t.media.expectedBytes.takeIf { it > 0 } ?: -1,
                category = t.category ?: if (t.media.audioOnly) DownloadCategory.Audio else DownloadCategory.Video,
            )
        }
        store.add(task)
        kicks.tryEmit(Unit)
        return task
    }

    fun addAll(requests: List<DownloadRequest>): List<DownloadTask> = requests.map { add(it) }

    fun get(id: String): DownloadTask? = store.get(id)

    fun pause(id: String) {
        val t = store.get(id) ?: return
        if (t.state == DownloadState.Completed || t.state == DownloadState.Failed) return
        val job = jobs[id]
        if (job != null) stopTargets[id] = DownloadState.Paused
        store.update(id) { it.copy(state = DownloadState.Paused, speedBps = 0) }
        job?.cancel()
    }

    /** Resume a paused task, or retry a failed one (from the start of what is still valid) */
    fun resume(id: String) {
        monitor.start()
        store.update(id) {
            if (it.state == DownloadState.Paused || it.state == DownloadState.Failed) {
                it.copy(state = DownloadState.Queued, error = null, errorKind = ErrorKind.None, retryCount = 0, nextRetryAt = 0)
            } else it
        }
        kicks.tryEmit(Unit)
    }

    /** The system ended the foreground service (6 hour limit of dataSync): the running downloads are paused, the user resumes them */
    fun pauseAllForSystem() {
        for (t in store.tasks.value.filter { it.state.isActive }) pause(t.id)
    }

    fun pauseAll() = store.tasks.value.filter { !it.state.isFinished && it.state != DownloadState.Paused }.forEach { pause(it.id) }

    fun resumeAll() = store.tasks.value.filter { it.state == DownloadState.Paused }.forEach { resume(it.id) }

    fun setPriority(id: String, priority: Int) {
        store.update(id) { it.copy(priority = priority) }
    }

    /** Speed limit of one task in bytes per second, 0 = none (a running torrent or media download picks it up) */
    fun setSpeedLimit(id: String, bytesPerSecond: Long) {
        store.update(id) { it.copy(speedLimitBps = bytesPerSecond.coerceAtLeast(0)) }
    }

    /** Runs before everything else that is waiting */
    fun moveToTop(id: String) {
        val max = store.tasks.value.filter { it.id != id }.maxOfOrNull { it.priority } ?: 0
        store.update(id) { if (it.priority > max) it else it.copy(priority = max + 1) }
        kicks.tryEmit(Unit)
    }

    /** Runs after everything else that is waiting */
    fun moveToBottom(id: String) {
        val min = store.tasks.value.filter { it.id != id }.minOfOrNull { it.priority } ?: 0
        store.update(id) { if (it.priority < min) it else it.copy(priority = min - 1) }
        kicks.tryEmit(Unit)
    }

    /**
     * Removes the task. An unfinished download always loses its partial file; the file of a finished one is
     * only deleted when [deleteFile] is true.
     */
    fun remove(id: String, deleteFile: Boolean) {
        val t = store.get(id) ?: return
        // out of the queue right away: the scheduler must not start it while it is being removed
        store.update(id) { if (it.state == DownloadState.Completed || it.state == DownloadState.Failed) it else it.copy(state = DownloadState.Paused, speedBps = 0) }
        scope.launch {
            val job = jobs[id]
            stopTargets[id] = DownloadState.Paused
            job?.cancel()
            job?.join()
            val latest = store.get(id) ?: t
            engines.firstOrNull { it.supports(latest) }?.let { runCatching { it.cleanup(latest, deleteFile) } }
            store.remove(id)
            stopTargets.remove(id)
            val uri = t.fileUri
            // torrents and media downloads delete their files in their engine's cleanup
            if (t.type == DownloadType.Http && uri != null && (deleteFile || t.state != DownloadState.Completed)) files.delete(uri)
        }
    }

    /** Unpacks a finished zip archive next to itself (also done automatically when "extract archives" is on) */
    fun extract(id: String) {
        val t = store.get(id) ?: return
        if (t.state != DownloadState.Completed || t.extractState == "running" || !de.mm20.launcher2.downloads.logic.ArchiveLogic.isZip(t.name)) return
        store.update(id) { it.copy(extractState = "running") }
        scope.launch(Dispatchers.IO) {
            try {
                ArchiveExtractor(files).extractZip(t, settings.current.defaultFolder)
                store.update(id) { it.copy(extractState = "done") }
            } catch (e: CancellationException) {
                store.update(id) { it.copy(extractState = "") }
                throw e
            } catch (e: Exception) {
                store.update(id) { it.copy(extractState = "failed") }
            }
        }
    }

    fun clearFinished(deleteFiles: Boolean = false) {
        store.tasks.value.filter { it.state == DownloadState.Completed }.forEach { remove(it.id, deleteFiles) }
    }

    // ---- scheduler

    private fun schedule() {
        val all = store.tasks.value
        val s = settings.current
        val c = monitor.conditions.value
        val now = System.currentTimeMillis()
        globalLimiter.bytesPerSecond = s.speedLimitKBps * 1024L

        val clock = currentClock()
        val reason = QueueRules.blockReason(c, s.queue, clock)
        _blockReason.value = reason
        if (reason != null) {
            // everything that runs waits until the conditions are good again; this is not a failure
            for (t in all.filter { it.state.isActive }) {
                stopTargets[t.id] = DownloadState.Queued
                jobs[t.id]?.cancel()
            }
        } else {
            for (t in QueueRules.pickNext(all, now, c, s.queue, clock) { task -> engines.any { it.supports(task) } }) startTask(t)
        }

        val after = store.tasks.value
        val running = after.any { it.state.isActive }
        val soon = reason == null && after.any { it.state == DownloadState.Queued && it.nextRetryAt - now < 10 * 60_000L }
        val wanted = running || soon
        if (wanted && !_serviceWanted.value) {
            _serviceWanted.value = true
            lastServiceStart = now
            DownloadService.start(context)
        } else if (wanted && !DownloadService.running && now - lastServiceStart > 5_000L) {
            // Android refused to start the service (app in the background): try again, it works once the app is visible
            lastServiceStart = now
            DownloadService.start(context)
        } else if (!wanted && _serviceWanted.value) {
            _serviceWanted.value = false
        }

        // the schedule window opens or closes: look again at that minute
        val windowChange = de.mm20.launcher2.downloads.logic.Schedule.minutesToChange(s.schedule, clock)
            ?.let { now + (it * 60L - java.util.Calendar.getInstance().get(java.util.Calendar.SECOND)) * 1000L + 200 }
        val wake = listOfNotNull(QueueRules.nextWakeUp(after, now), windowChange).minOrNull()
        if (wake == null) {
            wakeJob?.cancel(); wakeJob = null; wakeAt = 0
        } else if (wake != wakeAt) {
            wakeJob?.cancel()
            wakeAt = wake
            wakeJob = scope.launch {
                delay((wake - System.currentTimeMillis()).coerceAtLeast(0) + 50)
                kicks.tryEmit(Unit)
            }
        }
    }

    private fun currentClock(): de.mm20.launcher2.downloads.logic.ClockTime {
        val cal = java.util.Calendar.getInstance()
        val dow = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        return de.mm20.launcher2.downloads.logic.ClockTime(dow, cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE))
    }

    private fun startTask(picked: DownloadTask) {
        val engine = engines.first { it.supports(picked) }
        // a job that was just paused or cancelled may still be shutting down (closing its file, killing its process):
        // the new one starts when it is gone, its completion looks at the queue again
        if (jobs[picked.id]?.isActive == true) return
        // counts against the free slots right away
        store.update(picked.id) { it.copy(state = DownloadState.Connecting, speedBps = 0) }
        val job = scope.launch(Dispatchers.IO) { runTask(picked.id, engine) }
        jobs[picked.id] = job
        job.invokeOnCompletion {
            jobs.remove(picked.id, job)
            kicks.tryEmit(Unit)
        }
    }

    private suspend fun runTask(id: String, engine: DownloadEngine) {
        try {
            val task = store.get(id) ?: return
            engine.execute(task, SessionImpl(id))
            // a torrent reports completion itself at 100 % and then seeds; its job ends when seeding ends
            val alreadyReported = task.type == DownloadType.Torrent && (store.get(id)?.completedAt ?: 0L) > 0L
            val done = store.update(id) {
                it.copy(
                    state = DownloadState.Completed, completedAt = System.currentTimeMillis(), error = null,
                    errorKind = ErrorKind.None, retryCount = 0, speedBps = 0, downloadedBytes = if (it.totalBytes >= 0) it.totalBytes else it.downloadedBytes,
                )
            }
            if (done != null && !alreadyReported) {
                notifier.notifyCompleted(done)
                _events.tryEmit(DownloadEvent.Completed(done))
                if (settings.current.autoExtract) extract(id)
            }
        } catch (e: CancellationException) {
            val target = stopTargets.remove(id) ?: DownloadState.Paused
            store.update(id) { if (it.state.isActive) it.copy(state = target, speedBps = 0) else it }
            throw e
        } catch (e: DownloadException) {
            fail(id, e.kind, e.message ?: "Error", e.retryable)
        } catch (e: Exception) {
            fail(id, ErrorKind.Unknown, e.message ?: e.javaClass.simpleName, true)
        }
    }

    private fun fail(id: String, kind: ErrorKind, message: String, retryable: Boolean) {
        val t = store.get(id) ?: return
        val offline = !monitor.conditions.value.online
        val updated = when {
            kind == ErrorKind.Network && offline ->
                // lost the network: wait for it, no retry is used up
                store.update(id) { it.copy(state = DownloadState.Queued, speedBps = 0, error = message, errorKind = kind) }
            RetryPolicy.shouldRetry(retryable, t.retryCount, settings.current.maxRetries) -> {
                val attempt = t.retryCount + 1
                val wait = RetryPolicy.backoffMs(attempt) + Random.nextLong(0, 1000)
                store.update(id) {
                    it.copy(
                        state = DownloadState.Queued, retryCount = attempt, nextRetryAt = System.currentTimeMillis() + wait,
                        error = message, errorKind = kind, speedBps = 0,
                    )
                }
            }
            else -> store.update(id) { it.copy(state = DownloadState.Failed, error = message, errorKind = kind, speedBps = 0) }
        }
        if (updated != null && updated.state == DownloadState.Failed) {
            notifier.notifyFailed(updated)
            _events.tryEmit(DownloadEvent.Failed(updated))
        }
        kicks.tryEmit(Unit)
    }

    private inner class SessionImpl(private val id: String) : EngineSession {
        override val settings: DownloadSettingsValues get() = this@DownloadManager.settings.current
        override val globalLimiter: RateLimiter get() = this@DownloadManager.globalLimiter
        override val files: DownloadFiles get() = this@DownloadManager.files

        override suspend fun update(transform: (DownloadTask) -> DownloadTask): DownloadTask =
            store.update(id, transform = transform) ?: throw CancellationException("The download was removed")

        override suspend fun markCompleted() {
            val done = store.update(id) {
                it.copy(
                    completedAt = System.currentTimeMillis(), error = null, errorKind = ErrorKind.None, retryCount = 0,
                    downloadedBytes = if (it.totalBytes >= 0) it.totalBytes else it.downloadedBytes,
                )
            } ?: return
            notifier.notifyCompleted(done)
            _events.tryEmit(DownloadEvent.Completed(done))
        }

        override fun progress(downloadedBytes: Long, speedBps: Long, segments: List<SegmentState>) {
            // not saved on its own: the engines store their state regularly through update()
            store.update(id, save = false) {
                if (it.state.isActive) it.copy(downloadedBytes = downloadedBytes, speedBps = speedBps, segments = segments) else it
            }
        }
    }
}
