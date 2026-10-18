package de.mm20.launcher2.ui.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.RandomAccessFile

/**
 * Camera RAW files (DNG, CR2, NEF, ARW, …) almost always store a JPEG preview
 * inside a TIFF container. HEIC/HEIF is decoded by Android's BitmapFactory on
 * API 28+. HEVC elementary streams are video, not stills.
 */
internal object RawHeic {
    val rawExts = setOf(
        "dng", "raw", "arw", "cr2", "cr3", "nef", "nrw", "orf", "raf", "rw2", "pef",
        "sr2", "srf", "kdc", "dcr", "erf", "3fr", "mef", "mos", "x3f", "rwl",
    )
    val heicExts = setOf("heic", "heif", "hif")
    val hevcExts = setOf("hevc", "h265", "hvc", "hvc1")

    fun isRaw(ext: String) = ext.lowercase() in rawExts
    fun isHeic(ext: String) = ext.lowercase() in heicExts
    fun isHevc(ext: String) = ext.lowercase() in hevcExts

    fun decodeStill(file: File, req: Int): Bitmap? {
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inJustDecodeBounds = true }).let { }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= req && bounds.outHeight / (sample * 2) >= req) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeFile(file.path, opts)
        }
        val jpeg = embeddedJpeg(file) ?: return null
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    }

    /** First JPEG SOI..EOI inside a TIFF-like RAW, at most 8 MiB. */
    fun embeddedJpeg(file: File): ByteArray? {
        if (file.length() < 16 || file.length() > 256L * 1024 * 1024) return null
        RandomAccessFile(file, "r").use { raf ->
            val cap = minOf(file.length(), 32L * 1024 * 1024).toInt()
            val buf = ByteArray(cap)
            raf.readFully(buf)
            var i = 0
            while (i < buf.size - 3) {
                if (buf[i] == 0xFF.toByte() && buf[i + 1] == 0xD8.toByte() && buf[i + 2] == 0xFF.toByte()) {
                    var j = i + 3
                    while (j < buf.size - 1) {
                        if (buf[j] == 0xFF.toByte() && buf[j + 1] == 0xD9.toByte()) {
                            val size = j + 2 - i
                            if (size in 8 * 1024..8 * 1024 * 1024) return buf.copyOfRange(i, i + size)
                            break
                        }
                        j++
                    }
                }
                i++
            }
        }
        return null
    }
}
