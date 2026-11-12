package com.telos.desktop.theme

import com.telos.desktop.settings.TelosDesktopSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ThemeStudioSettingsTest {
    @Test
    fun intensityAccentDensityRoundTrip() {
        val dir = createTempDirectory("telos-theme-studio").toFile()
        try {
            val settings = TelosDesktopSettings(dir)
            settings.applyThemeIntensity(ThemeIntensity.VaultNeon)
            settings.applyThemeAccent(ThemeAccent.NeonCyan)
            settings.applyThemeDensity(ThemeDensity.Touch)
            settings.save()

            val text = File(dir, "desktop.properties").readText()
            assertTrue(text.contains("theme.intensity=neon"))
            assertTrue(text.contains("theme.accent=00F0FF"))
            assertTrue(text.contains("theme.density=touch"))
            assertTrue(text.contains("panelHeightDp=48"))
            assertTrue(text.contains("panelIconDp=32"))

            val loaded = TelosDesktopSettings(dir).also { it.load() }
            assertEquals(ThemeIntensity.VaultNeon.propertyValue, loaded.themeIntensity)
            assertEquals(ThemeAccent.NeonCyan.propertyValue, loaded.themeAccent)
            assertEquals(ThemeDensity.Touch.propertyValue, loaded.themeDensity)
            assertEquals(48f, loaded.panelHeightDp, 0.01f)
            assertEquals(32f, loaded.panelIconDp, 0.01f)
            assertEquals(ThemeDensity.Touch.snapPillScale, loaded.themeSnapPillScale, 0.01f)

            assertEquals(ThemeIntensity.VaultNeon, DesktopThemeChrome.intensity)
            assertEquals(ThemeAccent.NeonCyan, DesktopThemeChrome.accent)
            assertEquals(ThemeDensity.Touch, DesktopThemeChrome.density)
            assertTrue(DesktopThemeChrome.showGauges)
            assertTrue(DesktopThemeChrome.techBorderAlpha > 0.7f)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun quietOpsIsDefaultAndLowGlow() {
        val dir = createTempDirectory("telos-theme-quiet").toFile()
        try {
            val settings = TelosDesktopSettings(dir)
            settings.save()
            val loaded = TelosDesktopSettings(dir).also { it.load() }
            assertEquals(ThemeIntensity.QuietOps.propertyValue, loaded.themeIntensity)
            assertEquals(ThemeDensity.Comfort.propertyValue, loaded.themeDensity)
            assertFalse(DesktopThemeChrome.showGauges)
            assertTrue(DesktopThemeChrome.techBorderAlpha < 0.5f)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun wallpaperAccentPlaceholderPersists() {
        val dir = createTempDirectory("telos-theme-wallpaper").toFile()
        try {
            val settings = TelosDesktopSettings(dir)
            settings.applyThemeAccent(ThemeAccent.FromWallpaper)
            settings.applyThemeIntensity(ThemeIntensity.PaperOps)
            settings.applyThemeDensity(ThemeDensity.Compact)
            settings.save()
            val loaded = TelosDesktopSettings(dir).also { it.load() }
            assertEquals(ThemeAccent.FromWallpaper.propertyValue, loaded.themeAccent)
            assertEquals(ThemeIntensity.PaperOps.propertyValue, loaded.themeIntensity)
            assertEquals(32f, loaded.panelHeightDp, 0.01f)
            assertEquals(20f, loaded.panelIconDp, 0.01f)
            assertEquals(ThemeDensity.Compact.snapPillScale, loaded.themeSnapPillScale, 0.01f)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun parseAliases() {
        assertEquals(ThemeIntensity.VaultNeon, ThemeIntensity.parse("vault-neon"))
        assertEquals(ThemeIntensity.PaperOps, ThemeIntensity.parse("light"))
        assertEquals(ThemeAccent.NeonGreen, ThemeAccent.parse("#00E676"))
        assertEquals(ThemeAccent.FromWallpaper, ThemeAccent.parse("wallpaper"))
        assertEquals(ThemeDensity.Touch, ThemeDensity.parse("large"))
    }
}
