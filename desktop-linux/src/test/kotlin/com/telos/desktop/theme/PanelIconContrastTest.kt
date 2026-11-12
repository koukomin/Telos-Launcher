package com.telos.desktop.theme

import com.telos.desktop.settings.TelosDesktopSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory

class PanelIconContrastTest {
    @Before
    fun clear() {
        PanelIconContrast.clearCache()
    }

    @Test
    fun luminanceOfBlackIsNearZeroAndWhiteIsNearOne() {
        assertTrue(PanelIconContrast.relativeLuminance(0xFF000000.toInt()) < 0.01)
        assertTrue(PanelIconContrast.relativeLuminance(0xFFFFFFFF.toInt()) > 0.99)
    }

    @Test
    fun darkStripYieldsLightIconsAndLightStripYieldsDarkIcons() {
        assertEquals(
            PanelIconContrast.Tone.Light,
            PanelIconContrast.toneFromLuminance(0.10),
        )
        assertEquals(
            PanelIconContrast.Tone.Dark,
            PanelIconContrast.toneFromLuminance(0.80),
        )
        assertEquals(
            PanelIconContrast.Tone.Light,
            PanelIconContrast.toneFromLuminance(PanelIconContrast.LUMINANCE_MIDPOINT),
        )
    }

    @Test
    fun resolveToneHonoursForcedLightAndDarkOverLuminance() {
        assertEquals(
            PanelIconContrast.Tone.Light,
            PanelIconContrast.resolveTone(PanelIconContrast.Mode.Light, 0.95),
        )
        assertEquals(
            PanelIconContrast.Tone.Dark,
            PanelIconContrast.resolveTone(PanelIconContrast.Mode.Dark, 0.05),
        )
        assertEquals(
            PanelIconContrast.Tone.Dark,
            PanelIconContrast.resolveTone(PanelIconContrast.Mode.Auto, 0.70),
        )
        assertEquals(
            PanelIconContrast.Tone.Light,
            PanelIconContrast.resolveTone(PanelIconContrast.Mode.Auto, 0.20),
        )
    }

    @Test
    fun meanLuminanceOfUniformPixelsMatchesRelativeLuminance() {
        val dark = IntArray(16) { 0xFF101018.toInt() }
        val light = IntArray(16) { 0xFFE8ECF0.toInt() }
        assertEquals(
            PanelIconContrast.relativeLuminance(0xFF101018.toInt()),
            PanelIconContrast.meanLuminance(dark),
            1e-9,
        )
        assertEquals(
            PanelIconContrast.relativeLuminance(0xFFE8ECF0.toInt()),
            PanelIconContrast.meanLuminance(light),
            1e-9,
        )
        assertEquals(
            PanelIconContrast.Tone.Light,
            PanelIconContrast.toneFromLuminance(PanelIconContrast.meanLuminance(dark)),
        )
        assertEquals(
            PanelIconContrast.Tone.Dark,
            PanelIconContrast.toneFromLuminance(PanelIconContrast.meanLuminance(light)),
        )
    }

    @Test
    fun samplesTopStripAndCachesByDigest() {
        val dir = createTempDirectory("telos-panel-contrast").toFile()
        try {
            val file = File(dir, "wallpaper.png")
            writeStripWallpaper(file, topArgb = 0xFF0A0A12.toInt(), bottomArgb = 0xFFF0F0F0.toInt())
            val first = PanelIconContrast.sampleFile(file, PanelIconContrast.Mode.Auto)
            assertFalse(first.fromFallback)
            assertEquals(PanelIconContrast.Tone.Light, first.tone)
            assertTrue(first.meanLuminance < PanelIconContrast.LUMINANCE_MIDPOINT)

            val second = PanelIconContrast.sampleFile(file, PanelIconContrast.Mode.Auto)
            assertEquals(first.digest, second.digest)
            assertEquals(first.tone, second.tone)

            writeStripWallpaper(file, topArgb = 0xFFF5F5F5.toInt(), bottomArgb = 0xFF101010.toInt())
            val third = PanelIconContrast.sampleFile(file, PanelIconContrast.Mode.Auto)
            assertFalse(third.fromFallback)
            assertEquals(PanelIconContrast.Tone.Dark, third.tone)
            assertTrue(third.digest != first.digest)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun forcedModesSkipWallpaperSamplingTone() {
        val light = PanelIconContrast.sampleFile(null, PanelIconContrast.Mode.Light)
        assertEquals(PanelIconContrast.Tone.Light, light.tone)
        val dark = PanelIconContrast.sampleFile(null, PanelIconContrast.Mode.Dark)
        assertEquals(PanelIconContrast.Tone.Dark, dark.tone)
    }

    @Test
    fun parsesPrefAndPersistsPanelIconContrast() {
        val p = Properties()
        assertEquals(PanelIconContrast.Mode.Auto, PanelIconContrast.parseMode(p))
        p[PanelIconContrast.PREF_KEY] = "dark"
        assertEquals(PanelIconContrast.Mode.Dark, PanelIconContrast.parseMode(p))
        assertEquals(PanelIconContrast.Mode.Light, PanelIconContrast.parseMode("LIGHT"))
        assertEquals(PanelIconContrast.Mode.Auto, PanelIconContrast.parseMode("nope"))

        val dir = createTempDirectory("telos-panel-contrast-pref").toFile()
        try {
            val settings = TelosDesktopSettings(dir).also { it.load() }
            assertEquals("auto", settings.panelIconContrast)
            settings.panelIconContrast = "light"
            settings.save()
            val text = File(dir, "desktop.properties").readText()
            assertTrue(text.contains("panel.iconContrast=light"))
            val loaded = TelosDesktopSettings(dir).also { it.load() }
            assertEquals("light", loaded.panelIconContrast)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun writeStripWallpaper(file: File, topArgb: Int, bottomArgb: Int) {
        val img = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val strip = (64 * PanelIconContrast.PANEL_STRIP_FRACTION).toInt().coerceAtLeast(1)
        for (y in 0 until 64) {
            val c = if (y < strip) topArgb else bottomArgb
            for (x in 0 until 64) img.setRGB(x, y, c)
        }
        ImageIO.write(img, "png", file)
    }
}
