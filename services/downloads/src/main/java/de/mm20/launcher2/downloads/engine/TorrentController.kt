package de.mm20.launcher2.downloads.engine

import de.mm20.launcher2.downloads.DownloadStore
import de.mm20.launcher2.downloads.logic.FileSelection
import de.mm20.launcher2.downloads.logic.PeerFlagText
import de.mm20.launcher2.downloads.logic.PieceMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.swig.peer_info
import java.util.concurrent.ConcurrentHashMap

data class PeerRow(
    val ip: String,
    val client: String,
    val downBps: Long,
    val upBps: Long,
    val progress: Float,
    val flags: String,
    val totalDown: Long,
    val totalUp: Long,
)

enum class TrackerStatus { Working, Updating, NotContacted, Error }

data class TrackerRow(val url: String, val tier: Int, val status: TrackerStatus, val message: String, val fails: Int)

/**
 * What the UI can do with a torrent beyond what the manager offers (pause, resume, remove): look at its
 * peers, trackers and pieces, recheck it, reannounce, add a tracker, choose files, switch sequential mode.
 * Torrents that are running are reached through their handle in the session; for the others the choice is only
 * stored and used when the torrent starts. All functions may block on the session, call them off the main thread
 * (the suspend ones switch to IO themselves).
 */
class TorrentController(private val store: DownloadStore) {
    private val active = ConcurrentHashMap<String, TorrentHandle>()

    internal fun register(taskId: String, handle: TorrentHandle) {
        active[taskId] = handle
    }

    internal fun unregister(taskId: String) {
        active.remove(taskId)
    }

    fun isRunning(taskId: String) = active.containsKey(taskId)

    private fun handle(taskId: String): TorrentHandle? = active[taskId]?.takeIf { it.isValid }

    suspend fun peers(taskId: String): List<PeerRow> = withContext(Dispatchers.IO) {
        val h = handle(taskId) ?: return@withContext emptyList()
        try {
            val interesting = peer_info.interesting.to_int()
            val choked = peer_info.choked.to_int()
            val remoteChoked = peer_info.remote_choked.to_int()
            val remoteInterested = peer_info.remote_interested.to_int()
            val optimistic = peer_info.optimistic_unchoke.to_int()
            val snubbed = peer_info.snubbed.to_int()
            val local = peer_info.local_connection.to_int()
            val rc4 = peer_info.rc4_encrypted.to_int()
            val plain = peer_info.plaintext_encrypted.to_int()
            val utp = peer_info.utp_socket.to_int()
            h.peerInfo().map { p ->
                val f = p.flags()
                PeerRow(
                    ip = p.ip(), client = p.client().orEmpty(), downBps = p.downSpeed().toLong(), upBps = p.upSpeed().toLong(),
                    progress = p.progress(),
                    flags = PeerFlagText.describe(
                        interesting = f and interesting != 0, remoteChoked = f and remoteChoked != 0,
                        remoteInterested = f and remoteInterested != 0, choked = f and choked != 0,
                        optimistic = f and optimistic != 0, snubbed = f and snubbed != 0,
                        incoming = f and local == 0, encrypted = f and (rc4 or plain) != 0, utp = f and utp != 0,
                    ),
                    totalDown = p.totalDownload(), totalUp = p.totalUpload(),
                )
            }.sortedByDescending { it.downBps + it.upBps }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    suspend fun trackers(taskId: String): List<TrackerRow> = withContext(Dispatchers.IO) {
        val h = handle(taskId) ?: return@withContext emptyList()
        try {
            h.trackers().map { t ->
                // the state of a tracker is kept per network interface; the worst one counts
                val eps = t.endpoints().map { it.infohashV1() }
                val fails = eps.maxOfOrNull { it.fails().toInt() } ?: 0
                val message = eps.firstOrNull { !it.message().isNullOrBlank() }?.message().orEmpty()
                val status = when {
                    eps.any { it.isWorking() } -> TrackerStatus.Working
                    eps.any { it.updating() } -> TrackerStatus.Updating
                    fails > 0 -> TrackerStatus.Error
                    else -> TrackerStatus.NotContacted
                }
                TrackerRow(t.url(), t.tier(), status, message, fails)
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /** Fraction of finished pieces in [cells] cells; empty when the torrent is not running or has no metadata yet */
    suspend fun pieceMap(taskId: String, cells: Int): FloatArray = withContext(Dispatchers.IO) {
        val h = handle(taskId) ?: return@withContext FloatArray(0)
        try {
            val st = h.status(TorrentHandle.QUERY_PIECES)
            if (!st.hasMetadata()) return@withContext FloatArray(0)
            val bits = st.pieces()
            PieceMap.cells(st.numPieces(), cells) { bits.getBit(it) }
        } catch (e: Throwable) {
            FloatArray(0)
        }
    }

    suspend fun forceRecheck(taskId: String) = withContext(Dispatchers.IO) {
        runCatching { handle(taskId)?.forceRecheck() }
        Unit
    }

    suspend fun forceReannounce(taskId: String) = withContext(Dispatchers.IO) {
        runCatching {
            handle(taskId)?.apply {
                forceReannounce()
                forceDHTAnnounce()
            }
        }
        Unit
    }

    /** @return false when the address is not a tracker address or the torrent is not running */
    suspend fun addTracker(taskId: String, url: String): Boolean = withContext(Dispatchers.IO) {
        val u = url.trim()
        if (!isTrackerUrl(u)) return@withContext false
        val h = handle(taskId) ?: return@withContext false
        runCatching { h.addTracker(AnnounceEntry(u)); h.forceReannounce() }.isSuccess
    }

    /**
     * Changes file priorities (file index to 0..7). Works while the torrent runs and while it does not.
     * Not allowed to skip every file. @return false when refused.
     */
    suspend fun setFilePriorities(taskId: String, changes: Map<Int, Int>): Boolean = withContext(Dispatchers.IO) {
        val current = store.get(taskId)?.torrent ?: return@withContext false
        val next = FileSelection.apply(current.files, changes)
        if (!FileSelection.isValid(next)) return@withContext false
        store.update(taskId) { t ->
            val data = t.torrent ?: return@update t
            val files = FileSelection.apply(data.files, changes)
            t.copy(torrent = data.copy(files = files), totalBytes = FileSelection.selectedBytes(files).takeIf { it > 0 } ?: t.totalBytes)
        }
        val h = handle(taskId)
        if (h != null) runCatching { for ((i, p) in changes) h.filePriority(i, Priority.fromSwig(p.coerceIn(0, 7))) }
        true
    }

    suspend fun setSequential(taskId: String, sequential: Boolean) = withContext(Dispatchers.IO) {
        store.update(taskId) { t -> t.copy(torrent = t.torrent?.copy(sequential = sequential)) }
        val h = handle(taskId)
        if (h != null) runCatching {
            if (sequential) h.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD) else h.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
        }
        Unit
    }

    /** Seeding limits of this torrent; applied the next time the engine looks at them (within a second) */
    fun setSeedLimits(taskId: String, ratioX100: Int, minutes: Int, stopAtDone: Boolean) {
        store.update(taskId) { t ->
            t.copy(torrent = t.torrent?.copy(seedRatioX100 = ratioX100.coerceAtLeast(0), seedMinutes = minutes.coerceAtLeast(0), stopAtDone = stopAtDone))
        }
    }

    companion object {
        fun isTrackerUrl(u: String): Boolean {
            val l = u.lowercase()
            return (l.startsWith("http://") || l.startsWith("https://") || l.startsWith("udp://")) && u.length > 10 && !u.contains(' ')
        }
    }
}

