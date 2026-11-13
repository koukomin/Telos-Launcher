package com.telos.desktop.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class WallpaperTonalSeedTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun reset() {
        WallpaperTonalSeed.clearCache()
    }

    @Test
    fun usesWallpaperAccentFromPrefKeys() {
        assertTrue(WallpaperTonalSeed.usesWallpaperAccent("wallpaper", false))
        assertTrue(WallpaperTonalSeed.usesWallpaperAccent("Wallpaper", false))
        assertTrue(WallpaperTonalSeed.usesWallpaperAccent("neon-magenta", true))
        assertFalse(WallpaperTonalSeed.usesWallpaperAccent("neon-magenta", false))
        assertFalse(WallpaperTonalSeed.usesWallpaperAccent("neon-cyan", false))
    }

    @Test
    fun missingFileFallsBackToNeonCyan() {
        val missing = File(tmp.root, "no-such-wallpaper.png")
        val palette = WallpaperTonalSeed.sampleFile(missing)
        assertTrue(palette.fromFallback)
        assertEquals(WallpaperTonalSeed.FALLBACK_SEED_ARGB, palette.seedArgb)
        assertEquals(0xFF00F0FF.toInt(), palette.seedArgb)
    }

    @Test
    fun nullFileFallsBackToNeonCyan() {
        val palette = WallpaperTonalSeed.sampleFile(null)
        assertTrue(palette.fromFallback)
        assertEquals(WallpaperTonalSeed.FALLBACK_SEED_ARGB, palette.seedArgb)
    }

    @Test
    fun contentDigestCacheHitsAndInvalidatesOnChange() {
        val file = writeSolidPng("wall.png", 0xFF2244AA.toInt())
        val first = WallpaperTonalSeed.sampleFile(file)
        assertFalse(first.fromFallback)
        assertEquals(64, first.digest.length) // sha-256 hex
        val second = WallpaperTonalSeed.sampleFile(file)
        assertEquals(first.digest, second.digest)
        assertEquals(first.seedArgb, second.seedArgb)

        // Same path, different bytes → new digest / cache miss path.
        writeSolidPng("wall.png", 0xFF44AA22.toInt())
        val third = WallpaperTonalSeed.sampleFile(file)
        assertFalse(third.fromFallback)
        assertNotEquals(first.digest, third.digest)
    }

    @Test
    fun pathChangeInvalidatesCache() {
        val a = writeSolidPng("a.png", 0xFFFF007F.toInt())
        val b = writeSolidPng("b.png", 0xFF00E676.toInt())
        val pa = WallpaperTonalSeed.sampleFile(a)
        val pb = WallpaperTonalSeed.sampleFile(b)
        assertFalse(pa.fromFallback)
        assertFalse(pb.fromFallback)
        assertNotEquals(pa.digest, pb.digest)
        // Re-read a: digest must match first sample of a (cache by path+digest).
        val pa2 = WallpaperTonalSeed.sampleFile(a)
        assertEquals(pa.digest, pa2.digest)
        assertEquals(pa.seedArgb, pa2.seedArgb)
    }

    @Test
    fun seedFromPixelsPrefersChromaticPopulation() {
        val gray = IntArray(100) { 0xFF808080.toInt() }
        val cyan = IntArray(40) { 0xFF00F0FF.toInt() }
        val pixels = gray + cyan
        val seed = WallpaperTonalSeed.seedFromPixels(pixels)
        // Gray is filtered by chroma cutoff; cyanish seed should win (5-bit quantized).
        val r = (seed ushr 16) and 0xFF
        val g = (seed ushr 8) and 0xFF
        val b = seed and 0xFF
        assertEquals(0, r)
        assertTrue(g > 200)
        assertTrue(b > 200)
        assertTrue(kotlin.math.max(r, kotlin.math.max(g, b)) - kotlin.math.min(r, kotlin.math.min(g, b)) > 20)
    }

    @Test
    fun deriveContainersKeepsOpaqueAlpha() {
        val p = WallpaperTonalSeed.deriveContainers(
            seedArgb = 0xFF00F0FF.toInt(),
            digest = "test",
            wallpaperPath = null,
            fromFallback = false,
        )
        assertEquals(0xFF, (p.primaryContainerArgb ushr 24) and 0xFF)
        assertEquals(0xFF, (p.surfaceContainerArgb ushr 24) and 0xFF)
        assertEquals(0xFF, (p.secondaryContainerArgb ushr 24) and 0xFF)
    }

    @Test
    fun settingsRoundTripWallpaperKeys() {
        val dir = tmp.newFolder("config")
        val settings = com.telos.desktop.settings.TelosDesktopSettings(dir)
        settings.themeAccent = "wallpaper"
        settings.wallpaperTonal = true
        settings.wallpaperPath = "/vault/Pictures/wallpaper.png"
        settings.save()
        val loaded = com.telos.desktop.settings.TelosDesktopSettings(dir).also { it.load() }
        assertEquals("wallpaper", loaded.themeAccent)
        assertTrue(loaded.wallpaperTonal)
        assertEquals("/vault/Pictures/wallpaper.png", loaded.wallpaperPath)
        val props = File(dir, "desktop.properties").readText()
        assertTrue(props.contains("theme.accent=wallpaper"))
        assertTrue(props.contains("theme.wallpaperTonal=true"))
    }

    private fun writeSolidPng(name: String, argb: Int): File {
        val file = File(tmp.root, name)
        val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                img.setRGB(x, y, argb)
            }
        }
        ImageIO.write(img, "png", file)
        return file
    }
}
