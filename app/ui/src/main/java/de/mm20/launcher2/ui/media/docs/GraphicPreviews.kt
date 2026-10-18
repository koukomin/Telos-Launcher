package de.mm20.launcher2.ui.media.docs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.RandomAccessFile


/**
 * Flattened previews for formats Telos cannot fully render: PSD thumbnail, AI-as-PDF, SVG (caller uses Coil),
 * CDR embedded raster. Layers, vectors and editing are not reproduced.
 */
internal object GraphicPreviews {
    fun isAiPdf(file: File): Boolean {
        val head = ByteArray(2048)
        val n = file.inputStream().use { it.read(head) }
        if (n <= 0) return false
        val s = head.copyOf(n).toString(Charsets.ISO_8859_1)
        return s.startsWith("%PDF") || s.contains("%PDF-")
    }

    fun psdThumbnail(file: File): Bitmap? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val sig = ByteArray(4).also { raf.readFully(it) }
            if (String(sig, Charsets.ISO_8859_1) != "8BPS") return@use null
            raf.seek(26)
            val colorLen = raf.readInt().toLong().coerceAtLeast(0)
            raf.seek(raf.filePointer + colorLen)
            val resLen = raf.readInt().toLong().coerceAtLeast(0)
            val resEnd = raf.filePointer + resLen
            while (raf.filePointer + 12 <= resEnd) {
                val block = ByteArray(4).also { raf.readFully(it) }
                if (String(block, Charsets.ISO_8859_1) != "8BIM") break
                val id = raf.readUnsignedShort()
                val nameLen = raf.readUnsignedByte()
                val namePad = if (nameLen % 2 == 0) nameLen + 1 else nameLen
                raf.seek(raf.filePointer + namePad)
                val size = raf.readInt().toLong().coerceAtLeast(0)
                val dataStart = raf.filePointer
                if (id == 0x040C || id == 0x0409) {
                    val header = if (id == 0x040C) 28 else 28
                    if (size > header) {
                        raf.seek(raf.filePointer + header)
                        val jpeg = ByteArray((size - header).toInt().coerceAtMost(8 * 1024 * 1024))
                        raf.readFully(jpeg)
                        return@use BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                    }
                }
                raf.seek(dataStart + size + (size % 2))
            }
            null
        }
    }.getOrNull()

    /** First PNG or JPEG found in the first 8 MB (typical CorelDraw preview blob). */
    fun embeddedRaster(file: File): Bitmap? = runCatching {
        val cap = minOf(file.length(), 8L * 1024 * 1024).toInt()
        val buf = ByteArray(cap)
        file.inputStream().use { it.read(buf) }
        val png = indexOf(buf, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        if (png >= 0) {
            val end = indexOf(buf, byteArrayOf(0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()), png)
            val slice = if (end > png) buf.copyOfRange(png, (end + 8).coerceAtMost(buf.size)) else buf.copyOfRange(png, buf.size)
            BitmapFactory.decodeByteArray(slice, 0, slice.size)?.let { return@runCatching it }
        }
        val jpg = indexOf(buf, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        if (jpg >= 0) {
            val end = indexOf(buf, byteArrayOf(0xFF.toByte(), 0xD9.toByte()), jpg + 2)
            val slice = if (end > jpg) buf.copyOfRange(jpg, end + 2) else buf.copyOfRange(jpg, buf.size)
            BitmapFactory.decodeByteArray(slice, 0, slice.size)?.let { return@runCatching it }
        }
        null
    }.getOrNull()

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}


