package com.telos.desktop.settings

import com.telos.desktop.theme.ThemeIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

class ThemeAppearanceSettingsTest {
    @Test
    fun intensityAndWallpaperRoundTrip() {
        val dir = File.createTempFile("telos-settings", "").also {
            it.delete()
            it.mkdirs()
        }
        try {
            val settings = TelosDesktopSettings(dir)
            settings.themeIntensity = ThemeIntensity.VaultNeon.propertyValue
            settings.wallpaperPath = "Pictures/wall.png"
            settings.save()

            val loaded = TelosDesktopSettings(dir).also { it.load() }
            assertEquals(ThemeIntensity.VaultNeon.propertyValue, loaded.themeIntensity)
            assertEquals("Pictures/wall.png", loaded.wallpaperPath)

            val p = Properties()
            File(dir, "desktop.properties").inputStream().use { p.load(it) }
            assertEquals("neon", p.getProperty("theme.intensity"))
            assertEquals("Pictures/wall.png", p.getProperty("wallpaper.path"))
            assertEquals("Pictures/wall.png", p.getProperty("theme.wallpaperPath"))
            assertTrue(p.getProperty("themeIntensity") == "neon")
        } finally {
            dir.deleteRecursively()
        }
    }
}
