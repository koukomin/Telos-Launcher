package de.mm20.launcher2.wallpapers

import org.junit.Assert.*
import org.junit.Test

class GifWallpaperLimitsTest {
    private fun header(w: Int, h: Int) = "GIF89a".toByteArray(Charsets.US_ASCII) + byteArrayOf(w.toByte(), (w shr 8).toByte(), h.toByte(), (h shr 8).toByte())
    @Test fun readsUnsignedLittleEndianDimensions() { assertEquals(1920 to 1080, gifWallpaperDimensions(header(1920, 1080), 100_000)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsHugeDecodedFrame() { gifWallpaperDimensions(header(4096, 4096), 100) }
    @Test(expected = IllegalArgumentException::class) fun rejectsHugeInputBeforeDecode() { gifWallpaperDimensions(header(1, 1), 32L * 1024 * 1024 + 1) }
    @Test(expected = IllegalArgumentException::class) fun rejectsZeroDimensions() { gifWallpaperDimensions(header(0, 1), 100) }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidSignature() { gifWallpaperDimensions(ByteArray(10), 100) }
}
