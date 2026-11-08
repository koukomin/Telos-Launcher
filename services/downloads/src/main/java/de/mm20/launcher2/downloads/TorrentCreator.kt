package de.mm20.launcher2.downloads

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class TorrentResult(
    val name: String,
    val sizeBytes: Long,
    val torrentFile: File,
    val infoHashHex: String,
    val magnetUrl: String
)

object TorrentCreator {

    private val DEFAULT_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.bittorrent.eu:80/announce",
        "udp://explodie.org:6969/announce"
    )

    suspend fun create(context: Context, uri: Uri): Result<TorrentResult> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            var fileName = "file.dat"
            var fileSize = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Could not read file data")

            if (fileSize <= 0) fileSize = bytes.size.toLong()

            val pieceLength = 262144 // 256KB
            val sha1 = MessageDigest.getInstance("SHA-1")

            val pieces = ByteArray(((bytes.size + pieceLength - 1) / pieceLength) * 20)
            var pOffset = 0

            var i = 0
            while (i < bytes.size) {
                val len = minOf(pieceLength, bytes.size - i)
                sha1.reset()
                sha1.update(bytes, i, len)
                val hash = sha1.digest()
                System.arraycopy(hash, 0, pieces, pOffset, 20)
                pOffset += 20
                i += pieceLength
            }

            // Calculate InfoHash
            sha1.reset()
            val infoBencode = bencodeInfo(fileName, fileSize, pieceLength, pieces)
            sha1.update(infoBencode)
            val infoHash = sha1.digest()
            val infoHashHex = infoHash.joinToString("") { "%02x".format(it) }

            // Write Torrent File
            val torrentDir = File(context.cacheDir, "torrents").apply { mkdirs() }
            val torrentFile = File(torrentDir, "$fileName.torrent")

            val fullBencode = bencodeTorrent(DEFAULT_TRACKERS.first(), DEFAULT_TRACKERS, infoBencode)
            torrentFile.writeBytes(fullBencode)

            val trackerParams = DEFAULT_TRACKERS.joinToString("") { "&tr=" + Uri.encode(it) }
            val magnetUrl = "magnet:?xt=urn:btih:$infoHashHex&dn=${Uri.encode(fileName)}$trackerParams"

            TorrentResult(fileName, fileSize, torrentFile, infoHashHex, magnetUrl)
        }
    }

    private fun bencodeInfo(name: String, length: Long, pieceLength: Int, pieces: ByteArray): ByteArray {
        val sb = StringBuilder()
        sb.append("d")
        sb.append("6:lengthi").append(length).append("e")
        sb.append("4:name").append(name.length).append(":").append(name)
        sb.append("12:piece lengthi").append(pieceLength).append("e")
        sb.append("6:pieces").append(pieces.size).append(":")

        val prefix = sb.toString().toByteArray(Charsets.ISO_8859_1)
        val suffix = "e".toByteArray(Charsets.ISO_8859_1)

        val result = ByteArray(prefix.size + pieces.size + suffix.size)
        System.arraycopy(prefix, 0, result, 0, prefix.size)
        System.arraycopy(pieces, 0, result, prefix.size, pieces.size)
        System.arraycopy(suffix, 0, result, prefix.size + pieces.size, suffix.size)
        return result
    }

    private fun bencodeTorrent(announce: String, announceList: List<String>, infoBytes: ByteArray): ByteArray {
        val sb = StringBuilder()
        sb.append("d")
        sb.append("8:announce").append(announce.length).append(":").append(announce)
        sb.append("13:announce-listl")
        announceList.forEach { tr ->
            sb.append("l").append(tr.length).append(":").append(tr).append("e")
        }
        sb.append("e")
        sb.append("4:info")

        val prefix = sb.toString().toByteArray(Charsets.ISO_8859_1)
        val suffix = "e".toByteArray(Charsets.ISO_8859_1)

        val result = ByteArray(prefix.size + infoBytes.size + suffix.size)
        System.arraycopy(prefix, 0, result, 0, prefix.size)
        System.arraycopy(infoBytes, 0, result, prefix.size, infoBytes.size)
        System.arraycopy(suffix, 0, result, prefix.size + infoBytes.size, suffix.size)
        return result
    }
}
