package com.telos.desktop.cursor

import java.awt.image.BufferedImage
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class XCursorFrame(
    val width: Int,
    val height: Int,
    val xHot: Int,
    val yHot: Int,
    val delayMs: Int,
    val image: BufferedImage,
)

/**
 * Minimal XCursor (X11 cursor) binary reader.
 *
 * Loads the image chunk whose size is closest to [preferredSize]. Animated
 * multi-frame themes only expose the first matching frame — Compose/AWT do not
 * animate XCursor sequences here.
 */
object XCursorReader {
    private const val MAGIC = 0x72756358 // "Xcur" little-endian
    private const val IMAGE_TYPE = 0xfffd0002L

    fun loadBestFrame(file: File, preferredSize: Int): XCursorFrame? {
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(FileInputStream(file)).use { input ->
                val header = ByteArray(16)
                if (input.read(header) != 16) return null
                val hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                if (hb.int != MAGIC) return null
                val headerBytes = hb.int
                hb.int // version
                val ntoc = hb.int
                if (headerBytes > 16) {
                    val skip = headerBytes - 16
                    if (input.skipBytes(skip) < skip) return null
                }
                val toc = ArrayList<Toc>(ntoc.coerceAtMost(512))
                repeat(ntoc.coerceAtMost(512)) {
                    val entry = ByteArray(12)
                    if (input.read(entry) != 12) return@repeat
                    val eb = ByteBuffer.wrap(entry).order(ByteOrder.LITTLE_ENDIAN)
                    toc += Toc(
                        type = eb.int.toLong() and 0xffffffffL,
                        subtype = eb.int,
                        position = eb.int,
                    )
                }
                val images = toc.filter { it.type == IMAGE_TYPE }
                if (images.isEmpty()) return null
                val best = images.minByOrNull { kotlin.math.abs(it.subtype - preferredSize) } ?: return null
                readImageChunk(file, best.position)
            }
        }.getOrNull()
    }

    private fun readImageChunk(file: File, position: Int): XCursorFrame? {
        FileInputStream(file).use { fis ->
            if (fis.skip(position.toLong()) < position) return null
            val meta = ByteArray(36)
            if (fis.read(meta) != 36) return null
            val mb = ByteBuffer.wrap(meta).order(ByteOrder.LITTLE_ENDIAN)
            val type = mb.int.toLong() and 0xffffffffL
            if (type != IMAGE_TYPE) return null
            mb.int // chunk header size field
            mb.int // version
            val width = mb.int
            val height = mb.int
            val xhot = mb.int
            val yhot = mb.int
            val delay = mb.int
            if (width <= 0 || height <= 0 || width > 512 || height > 512) return null
            val pixels = width * height
            val raw = ByteArray(pixels * 4)
            var read = 0
            while (read < raw.size) {
                val n = fis.read(raw, read, raw.size - read)
                if (n < 0) return null
                read += n
            }
            val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val pb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val argb = pb.int
                    img.setRGB(x, y, argb)
                }
            }
            return XCursorFrame(width, height, xhot, yhot, delay, img)
        }
    }

    private data class Toc(val type: Long, val subtype: Int, val position: Int)
}
