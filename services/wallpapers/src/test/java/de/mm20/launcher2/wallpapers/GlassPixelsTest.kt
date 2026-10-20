package de.mm20.launcher2.wallpapers

import org.junit.Assert.*
import org.junit.Test

class GlassPixelsTest {
    @Test fun largePhotosAreSubsampledBeforeAllocation() {
        assertEquals(16, GlassPixels.sampleSize(8000, 6000))
        assertThrows(IllegalArgumentException::class.java) { GlassPixels.sampleSize(100000, 100000) }
    }
    @Test fun blurRadiusUsesTheActualCenterCropScale() {
        assertEquals(0, GlassPixels.sourceRadius(0, 512, 256, 400, 800))
        assertEquals(10, GlassPixels.sourceRadius(32, 512, 256, 400, 800))
        assertEquals(20, GlassPixels.sourceRadius(32, 512, 256, 800, 400))
    }
    @Test fun flatColorsSurviveBlurAndInputRemainsImmutable() {
        val pixels = IntArray(8 * 8) { 0xff18293a.toInt() }
        assertArrayEquals(pixels, GlassPixels.blur(pixels, 8, 8, 4))
        assertArrayEquals(IntArray(64) { 0xff18293a.toInt() }, pixels)
    }
    @Test fun edgesAreClampedAndHighFrequencyDetailsAreActuallyBlurred() {
        val input = intArrayOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xff000000.toInt())
        val output = GlassPixels.blur(input, 3, 1, 1)
        assertTrue((output[1] and 255) in 1..254)
        assertEquals(output[0], output[2])
        assertTrue(output.all { it ushr 24 == 255 })
    }
}
