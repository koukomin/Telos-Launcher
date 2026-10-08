package de.mm20.launcher2.comms.media.video.torrent

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

/** One video file inside a torrent, playable through the local stream address in [url]. */
data class TorrentVideo(val index: Int, val name: String, val sizeBytes: Long, val url: String)

/** The video files of an opened torrent in file order, and the one to start with (the biggest) */
data class TorrentOpened(val videos: List<TorrentVideo>, val startPosition: Int)

data class TorrentState(
    val stage: Stage = Stage.Idle,
    val name: String = "",
    val peers: Int = 0,
    val downloadBytesPerSecond: Long = 0,
    val progress: Float = 0f,
    val message: String = "",
) {
    enum class Stage { Idle, FindingPeers, Ready, Failed }
}

/**
 * Plays torrents while they download: the pieces of the chosen video are fetched in order, and the
 * player reads them through a small web server that only listens on this phone (127.0.0.1) and
 * waits for a piece when the player asks for one that has not arrived yet.
 *
 * Built on libtorrent (via libtorrent4j, MIT). Torrents are a way to receive files: use it only for
 * content you are allowed to watch. Nothing runs until a torrent is opened, and everything is closed
 * and deleted when the player is closed.
 */
object TorrentStreamer {

    private val _state = MutableStateFlow(TorrentState())
    val state: StateFlow<TorrentState> = _state

    @Volatile private var handle: TorrentHandle? = null
    @Volatile private var info: TorrentInfo? = null
    @Volatile private var saveDir: File? = null
    private var server: ServerSocket? = null
    private val sockets = java.util.Collections.synchronizedSet(HashSet<Socket>())
    private var token = ""
    @Volatile private var closed = true
    private val lock = Any()
    /** The screen that opened the current torrent, only it may close it again */
    private var owner: Any? = null
    private val ioPool by lazy { Executors.newCachedThreadPool() }
    /** Counts the closes: an [open] that finds a newer number was closed (or replaced) while it waited for peers */
    private val generation = java.util.concurrent.atomic.AtomicInteger()

    /** True when the connection is metered (mobile data) */
    fun onMeteredNetwork(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun isTorrent(source: String): Boolean {
        val s = source.trim().lowercase()
        return s.startsWith("magnet:") || s.substringBefore('?').endsWith(".torrent")
    }

    /**
     * Opens a magnet link, a .torrent address or a .torrent file and returns its video files.
     * [wifiOnly] refuses to start on mobile data.
     */
    suspend fun open(context: Context, source: String, wifiOnly: Boolean, owner: Any? = null): TorrentOpened =
        withContext(Dispatchers.IO) {
            val myGeneration: Int
            synchronized(lock) {
                close()
                this@TorrentStreamer.owner = owner
                myGeneration = generation.get()
            }
            fun alive() = generation.get() == myGeneration
            if (wifiOnly && onMeteredNetwork(context)) {
                fail("Torrent streaming is set to Wi-Fi only")
            }
            closed = false
            _state.value = TorrentState(TorrentState.Stage.FindingPeers, message = "Finding peers…")
            // a new folder for every open: the cleanup of the previous one runs on another thread
            File(context.cacheDir, "torrent").listFiles()?.forEach { it.deleteRecursively() }
            val dir = File(context.cacheDir, "torrent/${System.nanoTime()}").apply { mkdirs() }
            saveDir = dir
            try {
                // the one session of Telos, shared with the torrent downloads of Telos Downloads
                if (!alive()) throw kotlinx.coroutines.CancellationException("The torrent was closed")
                val sm = TorrentSession.acquire(context, this@TorrentStreamer)

                val data: ByteArray = when {
                    source.startsWith("magnet:", ignoreCase = true) ->
                        sm.fetchMagnet(source, 90, File(dir, "meta").apply { mkdirs() })
                            ?: fail("No answer from peers, the link may be dead")
                    source.startsWith("content:") || source.startsWith("file:") ->
                        context.contentResolver.openInputStream(Uri.parse(source))!!.use { readLimited(it) }
                    else -> (URL(source).openConnection() as java.net.HttpURLConnection).run {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        try {
                            if (contentLengthLong > MAX_TORRENT_FILE) fail("The torrent file is too large")
                            inputStream.use { readLimited(it) }
                        } finally { disconnect() }
                    }
                }
                // closed while waiting for peers (the player was left): nothing may be started any more
                if (!alive()) throw kotlinx.coroutines.CancellationException("The torrent was closed")
                val ti = TorrentInfo.bdecode(data)
                info = ti
                val fs = ti.files()

                val videos = (0 until ti.numFiles())
                    .filter { isVideo(fs.filePath(it)) }
                    .sortedBy { fs.filePath(it).lowercase() }
                if (videos.isEmpty()) fail("This torrent has no video file")

                // only the video files are downloaded, the rest is ignored
                val priorities = Array(ti.numFiles()) { Priority.IGNORE }
                videos.forEach { priorities[it] = Priority.DEFAULT }
                sm.download(ti, dir, null, priorities, null, null)
                val h = sm.find(ti.infoHash()) ?: fail("Could not start the download")
                h.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                // not part of the queue of the session (its limits are for the downloads), and not paused by it
                h.unsetFlags(TorrentFlags.AUTO_MANAGED)
                h.resume()
                handle = h

                token = UUID.randomUUID().toString().replace("-", "")
                val port = startServer()
                // prefer the biggest file for the first piece priorities
                val biggest = videos.maxByOrNull { fs.fileSize(it) }!!
                prioritiseEnds(biggest)

                _state.value = TorrentState(TorrentState.Stage.Ready, name = ti.name(), message = "Buffering…")
                refreshStatus()

                val list = videos.map {
                    TorrentVideo(
                        index = it,
                        name = fs.fileName(it),
                        sizeBytes = fs.fileSize(it),
                        url = "http://127.0.0.1:$port/$token/$it",
                    )
                }
                TorrentOpened(list, list.indexOfFirst { it.index == biggest }.coerceAtLeast(0))
            } catch (e: Exception) {
                // a newer open (or a close) owns the state now: leave it alone
                if (alive()) {
                    val message = e.message ?: "Torrent could not be opened"
                    close()
                    _state.value = TorrentState(TorrentState.Stage.Failed, message = message)
                }
                throw e
            }
        }

    private const val MAX_TORRENT_FILE = 8_000_000

    /** A .torrent file is small; a stream of unknown length must not fill the memory */
    private fun readLimited(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_TORRENT_FILE) fail("The torrent file is too large")
        }
        return out.toByteArray()
    }

    /** Re-applies the peer block lists to the running session, called after a list changed. */
    fun reapplyBlockList() = TorrentSession.reapplyBlockList()

    private fun fail(message: String): Nothing = throw IllegalStateException(message)

    private fun isVideo(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf("mkv", "mp4", "avi", "mov", "webm", "m4v", "ts", "mpg", "mpeg", "wmv", "flv")
    }

    /** The first and last pieces of a video are needed first (headers and the index of MP4 files). */
    private fun prioritiseEnds(fileIndex: Int) {
        val ti = info ?: return
        val h = handle ?: return
        val fs = ti.files()
        val pieceLength = ti.pieceLength().toLong()
        val start = fs.fileOffset(fileIndex)
        val end = start + fs.fileSize(fileIndex) - 1
        val first = (start / pieceLength).toInt()
        val last = (end / pieceLength).toInt()
        val head = maxOf(2, (4L * 1024 * 1024 / pieceLength).toInt())
        for (p in first..minOf(last, first + head)) h.piecePriority(p, Priority.TOP_PRIORITY)
        for (p in maxOf(first, last - 1)..last) h.piecePriority(p, Priority.TOP_PRIORITY)
    }

    /** Updates the numbers shown in the player (peers, speed, progress). */
    fun refreshStatus() {
        if (closed) return
        val h = handle ?: return
        runCatching {
            val s = h.status()
            _state.value = _state.value.copy(
                peers = s.numPeers(),
                downloadBytesPerSecond = s.downloadPayloadRate().toLong(),
                progress = s.progress(),
            )
        }
    }

    // ---- local web server ----

    private fun startServer(): Int {
        val ss = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        server = ss
        ioPool.execute {
            de.mm20.launcher2.base.contained("torrent server") {
                while (!closed && !ss.isClosed) {
                    val socket = try { ss.accept() } catch (e: Exception) { break }
                    sockets.add(socket)
                    ioPool.execute { try { serve(socket) } catch (e: Exception) { /* connection dropped */ } finally { sockets.remove(socket) } }
                }
            }
        }
        return ss.localPort
    }

    private fun serve(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 0
            val input = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val requestLine = input.readLine() ?: return
            val headers = HashMap<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
            }
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].trim('/').split("/")
            val out = s.getOutputStream()
            val ti = info
            val h = handle
            if (path.size != 2 || path[0] != token || ti == null || h == null) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            val fileIndex = path[1].toIntOrNull()
            if (fileIndex == null || fileIndex !in 0 until ti.numFiles()) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            val fs = ti.files()
            val size = fs.fileSize(fileIndex)
            // The file is wanted now: make sure it is downloaded even if it was not the first choice
            if (h.filePriorities()[fileIndex] == Priority.IGNORE) {
                h.filePriority(fileIndex, Priority.DEFAULT)
                prioritiseEnds(fileIndex)
            }

            var start = 0L
            var end = size - 1
            val range = headers["range"]
            if (range != null && range.startsWith("bytes=")) {
                val (a, b) = range.removePrefix("bytes=").split("-").let { it[0] to it.getOrElse(1) { "" } }
                try {
                    if (a.isNotEmpty()) {
                        start = a.toLong()
                        if (b.isNotEmpty()) end = minOf(b.toLong(), size - 1)
                    } else if (b.isNotEmpty()) {
                        start = maxOf(0, size - b.toLong())
                    }
                } catch (e: NumberFormatException) {
                    out.write("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    return
                }
            }
            if (start >= size || start > end) {
                out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            val length = end - start + 1
            val partial = range != null
            val status = if (partial) "206 Partial Content" else "200 OK"
            val sb = StringBuilder("HTTP/1.1 $status\r\n")
            sb.append("Content-Type: ").append(mime(fs.filePath(fileIndex))).append("\r\n")
            sb.append("Accept-Ranges: bytes\r\nContent-Length: $length\r\n")
            if (partial) sb.append("Content-Range: bytes $start-$end/$size\r\n")
            sb.append("Connection: close\r\n\r\n")
            out.write(sb.toString().toByteArray())
            if (method == "HEAD") return

            val file = File(saveDir, fs.filePath(fileIndex))
            val chunk = 256 * 1024
            val buffer = ByteArray(chunk)
            var position = start
            var raf: RandomAccessFile? = null
            try {
                while (position <= end && !closed) {
                    val want = minOf(chunk.toLong(), end - position + 1).toInt()
                    if (!waitForBytes(fileIndex, position, want)) return
                    if (raf == null) {
                        // the file appears on disk once its first piece is written
                        raf = RandomAccessFile(file, "r")
                    }
                    raf.seek(position)
                    val read = raf.read(buffer, 0, want)
                    if (read <= 0) return
                    out.write(buffer, 0, read)
                    position += read
                }
                out.flush()
            } finally {
                runCatching { raf?.close() }
            }
        }
    }

    /** Blocks until all pieces that cover the bytes are downloaded; false when closed or timed out. */
    private fun waitForBytes(fileIndex: Int, offsetInFile: Long, length: Int): Boolean {
        val ti = info ?: return false
        val h = handle ?: return false
        val pieceLength = ti.pieceLength().toLong()
        val absolute = ti.files().fileOffset(fileIndex) + offsetInFile
        val first = (absolute / pieceLength).toInt()
        val last = ((absolute + length - 1) / pieceLength).toInt()
        val waitedSince = System.currentTimeMillis()
        var deadline = 250
        for (p in first..last) {
            if (h.havePiece(p)) continue
            // time critical: libtorrent fetches this piece before the others
            runCatching { h.setPieceDeadline(p, deadline) }
            deadline += 150
        }
        while (!closed) {
            var all = true
            for (p in first..last) if (!h.havePiece(p)) { all = false; break }
            if (all) return true
            if (System.currentTimeMillis() - waitedSince > 10 * 60 * 1000) return false
            Thread.sleep(200)
        }
        return false
    }

    private fun mime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts" -> "video/mp2t"
        else -> "application/octet-stream"
    }

    /** Name of the torrent and its files without the stream address, for subtitle searches */
    fun torrentName(): String = info?.name().orEmpty()

    /**
     * Closes the torrent that [owner] opened, off the calling thread (stopping the session can
     * block). Does nothing when another screen has opened a torrent in the meantime.
     */
    fun release(owner: Any) {
        Thread {
            synchronized(lock) { if (this.owner === owner) close() }
        }.start()
    }

    /** Stops the download and the local server and deletes the downloaded data. */
    fun close() {
        generation.incrementAndGet()
        owner = null
        closed = true
        runCatching { server?.close() }
        server = null
        synchronized(sockets) { sockets.forEach { runCatching { it.close() } }; sockets.clear() }
        // only this torrent goes; the session stays when Telos Downloads uses it
        val h = handle
        if (h != null) runCatching { TorrentSession.current()?.remove(h) }
        TorrentSession.release(this)
        handle = null
        info = null
        saveDir?.let { dir -> Thread { runCatching { dir.deleteRecursively() } }.start() }
        saveDir = null
        _state.value = TorrentState()
    }
}
