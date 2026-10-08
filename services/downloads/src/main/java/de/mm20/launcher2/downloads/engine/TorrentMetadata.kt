package de.mm20.launcher2.downloads.engine

import android.content.Context
import android.net.Uri
import de.mm20.launcher2.comms.media.video.torrent.TorrentSession
import de.mm20.launcher2.downloads.DownloadException
import de.mm20.launcher2.downloads.ErrorKind
import de.mm20.launcher2.downloads.TorrentFile
import de.mm20.launcher2.downloads.logic.TorrentPaths
import de.mm20.launcher2.downloads.logic.TorrentSourceKind
import de.mm20.launcher2.downloads.logic.TorrentSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** What a torrent contains, read before it is added: name, size and the file list */
class TorrentPreview(
    val name: String,
    val infoHash: String,
    val totalSize: Long,
    val files: List<TorrentFile>,
    val pieceLength: Int,
    val numPieces: Int,
    val isPrivate: Boolean,
    val rootFolder: String,
    /** The .torrent file, to be stored with the task */
    val torrentBytes: ByteArray,
)

object TorrentMetadata {
    private const val MAX_TORRENT_BYTES = 8L * 1024 * 1024

    /**
     * Reads a .torrent file (content:, file: or http(s) address) or asks the swarm for the metadata of a magnet link
     * (this needs peers, DHT or trackers and can take up to [timeoutSeconds]).
     */
    suspend fun load(context: Context, source: String, timeoutSeconds: Int = 75): TorrentPreview = withContext(Dispatchers.IO) {
        // interruptible: closing the sheet ends the wait for the swarm instead of holding the session for the full timeout
        val bytes = runInterruptible { when (TorrentSources.classify(source)) {
            TorrentSourceKind.Magnet -> fetchMagnet(context, source.trim(), timeoutSeconds)
            TorrentSourceKind.TorrentUrl -> download(source.trim())
            TorrentSourceKind.TorrentFile -> read(context, source.trim())
            TorrentSourceKind.None -> throw DownloadException(ErrorKind.Validation, "This is not a magnet link or a .torrent address", false)
        } }
        parse(bytes)
    }

    fun parse(bytes: ByteArray): TorrentPreview {
        val ti = try {
            TorrentInfo.bdecode(bytes)
        } catch (e: Throwable) {
            throw DownloadException(ErrorKind.Validation, "This is not a valid .torrent file", false, e)
        }
        if (!ti.isValid || ti.numFiles() <= 0) throw DownloadException(ErrorKind.Validation, "This is not a valid .torrent file", false)
        val fs = ti.files()
        val files = ArrayList<TorrentFile>()
        for (i in 0 until ti.numFiles()) {
            if (fs.padFileAt(i)) continue
            files.add(TorrentFile(index = i, path = fs.filePath(i).replace('\\', '/'), size = fs.fileSize(i)))
        }
        if (files.isEmpty()) throw DownloadException(ErrorKind.Validation, "The torrent has no files", false)
        return TorrentPreview(
            name = ti.name(),
            infoHash = ti.infoHashes().best.toHex().lowercase(),
            totalSize = ti.totalSize(),
            files = files,
            pieceLength = ti.pieceLength(),
            numPieces = ti.numPieces(),
            isPrivate = ti.isPrivate,
            rootFolder = TorrentPaths.rootFolder(files.map { it.path }),
            torrentBytes = bytes,
        )
    }

    private fun fetchMagnet(context: Context, magnet: String, timeoutSeconds: Int): ByteArray {
        val owner = Any()
        val dir = File(context.cacheDir, "torrent-meta/${System.nanoTime()}").apply { mkdirs() }
        try {
            val sm = TorrentSession.acquire(context, owner)
            return sm.fetchMagnet(magnet, timeoutSeconds, dir)
                ?: throw DownloadException(ErrorKind.Network, "No answer from peers. The link may be dead, or DHT and trackers are off.", true)
        } finally {
            TorrentSession.release(owner)
            dir.deleteRecursively()
        }
    }

    private fun download(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "TelosDownloads")
            val code = c.responseCode
            if (code !in 200..299) throw DownloadException(ErrorKind.Http, "HTTP $code", code >= 500)
            if (c.contentLengthLong > MAX_TORRENT_BYTES) throw DownloadException(ErrorKind.Validation, "The torrent file is too large", false)
            return readLimited(c.inputStream)
        } catch (e: DownloadException) {
            throw e
        } catch (e: java.io.IOException) {
            throw DownloadException(ErrorKind.Network, e.message ?: "Network error", true, e)
        } finally {
            c.disconnect()
        }
    }

    private fun read(context: Context, source: String): ByteArray {
        val uri = Uri.parse(source)
        val stream = try {
            if (uri.scheme == "file") File(uri.path!!).inputStream() else context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        } ?: throw DownloadException(ErrorKind.Storage, "The .torrent file can not be read", false)
        return readLimited(stream)
    }

    private fun readLimited(input: java.io.InputStream): ByteArray = input.use {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = it.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_TORRENT_BYTES) throw DownloadException(ErrorKind.Validation, "The torrent file is too large", false)
        }
        out.toByteArray()
    }
}
