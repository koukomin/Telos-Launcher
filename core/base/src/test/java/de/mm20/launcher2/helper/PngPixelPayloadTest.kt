package de.mm20.launcher2.helper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PngPixelPayloadTest {
    @get:Rule
    val files = TemporaryFolder()

    @Test
    fun storesPayloadOnlyInBlueBitsOfOpaquePixels() {
        val payload = ByteArray(64) { it.toByte() }
        val input = files.newFile().apply { writeBytes(payload) }
        val output = files.newFile()
        val pixels = IntArray(2048) { if (it % 2 == 0) 0xffaabbcc.toInt() else 0x88aabbcc.toInt() }
        val original = pixels.copyOf()

        PngPixelPayload.embed(pixels, input) {}
        PngPixelPayload.extract(pixels, output) {}

        assertArrayEquals(payload, output.readBytes())
        assertTrue(pixels.indices.all { i ->
            if (original[i] ushr 24 == 255) (pixels[i] xor original[i]) in 0..1
            else pixels[i] == original[i]
        })
        assertTrue(runCatching { PngPixelPayload.embed(IntArray(64), input) {} }.isFailure)
        assertTrue(runCatching { PngPixelPayload.extract(original, output) {} }.isFailure)
    }
}
