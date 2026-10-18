package de.mm20.launcher2.ui.files

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/** One file or folder on an ISO 9660 / Joliet disc image. */
internal data class IsoItem(val path: String, val isDir: Boolean, val size: Long, val lba: Long, val modified: Long)

/**
 * Reads a CD/DVD image (ISO 9660, Joliet when present). Android has no FUSE, so Telos
 * shows the image as a folder the same way it shows a zip.
 */
internal class Iso9660(private val file: File) {
    val items: List<IsoItem> by lazy { index() }

    fun open(path: String): InputStream {
        val item = items.firstOrNull { it.path == path && !it.isDir } ?: throw IOException("Not found in the image")
        val raf = RandomAccessFile(file, "r")
        val start = item.lba * SECTOR
        return object : InputStream() {
            private var pos = 0L
            override fun read(): Int {
                val b = ByteArray(1)
                val n = read(b, 0, 1)
                return if (n <= 0) -1 else b[0].toInt() and 0xFF
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= item.size) return -1
                val n = minOf(len.toLong(), item.size - pos).toInt()
                synchronized(raf) {
                    raf.seek(start + pos)
                    val got = raf.read(b, off, n)
                    if (got > 0) pos += got
                    return got
                }
            }
            override fun close() { runCatching { raf.close() } }
        }
    }

    private fun index(): List<IsoItem> {
        RandomAccessFile(file, "r").use { raf ->
            var pvdRoot: DirRef? = null
            var jolietRoot: DirRef? = null
            var sector = 16L
            while (sector < 32L) {
                val block = ByteArray(SECTOR)
                raf.seek(sector * SECTOR)
                if (raf.read(block) < SECTOR) break
                val type = block[0].toInt() and 0xFF
                if (type == 255) break
                if (type == 1 && pvdRoot == null) pvdRoot = dirFromDescriptor(block)
                if (type == 2 && isJoliet(block)) jolietRoot = dirFromDescriptor(block)
                sector++
            }
            val root = jolietRoot ?: pvdRoot ?: throw IOException("Not an ISO 9660 image")
            val found = LinkedHashMap<String, IsoItem>()
            walk(raf, root, "", jolietRoot != null, found)
            return found.values.toList()
        }
    }

    private data class DirRef(val lba: Long, val size: Long)

    private fun dirFromDescriptor(block: ByteArray): DirRef {
        val rec = block.copyOfRange(156, 190)
        return DirRef(u32le(rec, 2).toLong(), u32le(rec, 10).toLong())
    }

    private fun isJoliet(block: ByteArray): Boolean =
        block[88] == 0x25.toByte() && block[89] == 0x2F.toByte() && (block[90] == 0x45.toByte() || block[90] == 0x40.toByte() || block[90] == 0x43.toByte())

    private fun walk(raf: RandomAccessFile, dir: DirRef, prefix: String, joliet: Boolean, out: LinkedHashMap<String, IsoItem>) {
        var offset = 0L
        val data = ByteArray(dir.size.toInt().coerceAtLeast(1))
        raf.seek(dir.lba * SECTOR)
        raf.readFully(data, 0, minOf(data.size, dir.size.toInt()))
        while (offset < dir.size) {
            val recLen = data[offset.toInt()].toInt() and 0xFF
            if (recLen == 0) {
                val next = ((offset / SECTOR) + 1) * SECTOR
                if (next <= offset) break
                offset = next
                continue
            }
            val rec = data.copyOfRange(offset.toInt(), offset.toInt() + recLen)
            offset += recLen
            val flags = rec[25].toInt() and 0xFF
            val idLen = rec[32].toInt() and 0xFF
            if (idLen <= 0) continue
            val rawName = rec.copyOfRange(33, 33 + idLen)
            val name = decodeName(rawName, joliet)
            if (name == "." || name == "..") continue
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            val isDir = flags and 0x02 != 0
            val lba = u32le(rec, 2).toLong()
            val size = u32le(rec, 10).toLong()
            val modified = decodeDate(rec, 18)
            out[path] = IsoItem(path, isDir, if (isDir) -1 else size, lba, modified)
            if (isDir) walk(raf, DirRef(lba, size), path, joliet, out)
        }
    }

    private fun decodeName(bytes: ByteArray, joliet: Boolean): String {
        val raw = if (joliet) String(bytes, Charset.forName("UTF-16BE")).trimEnd('\u0000')
        else String(bytes, Charsets.ISO_8859_1)
        return raw.substringBefore(';').trim().ifEmpty { raw }
    }

    private fun decodeDate(rec: ByteArray, off: Int): Long {
        if (rec.size < off + 7) return 0
        val y = (rec[off].toInt() and 0xFF) + 1900
        val mo = rec[off + 1].toInt() and 0xFF
        val d = rec[off + 2].toInt() and 0xFF
        val h = rec[off + 3].toInt() and 0xFF
        val mi = rec[off + 4].toInt() and 0xFF
        val s = rec[off + 5].toInt() and 0xFF
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.set(y, (mo - 1).coerceIn(0, 11), d.coerceIn(1, 31), h, mi, s)
        return cal.timeInMillis
    }

    private fun u32le(b: ByteArray, off: Int): Int =
        ByteBuffer.wrap(b, off, 4).order(ByteOrder.LITTLE_ENDIAN).int

    companion object {
        private const val SECTOR = 2048
    }
}
