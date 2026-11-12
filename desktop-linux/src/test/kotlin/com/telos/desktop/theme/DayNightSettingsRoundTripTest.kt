package com.telos.desktop.theme

import com.telos.desktop.settings.TelosDesktopSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DayNightSettingsRoundTripTest {
    @Test
    fun defaultsAreManualTimezoneNoNetwork() {
        val dir = createTempDirectory("telos-daynight").toFile()
        try {
            val s = TelosDesktopSettings(dir)
            assertEquals(DayNightMode.Off.propertyValue, s.themeDayNight)
            assertFalse(s.themeAutoTimezone)
            assertFalse(s.locationNetworkAssist)
            assertTrue(s.themeTimezone.isNotBlank())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun roundTripDayNightPrefs() {
        val dir = createTempDirectory("telos-daynight-rt").toFile()
        try {
            val s = TelosDesktopSettings(dir)
            s.themeDayNight = DayNightMode.Auto.propertyValue
            s.themeTimezone = "Europe/Athens"
            s.themeAutoTimezone = true
            s.locationNetworkAssist = true
            s.locationCountryCode = "GR"
            s.themeLatitude = 37.98
            s.themeLongitude = 23.73
            s.themeIntensity = DayNightPhase.PaperOps.intensityProperty
            s.save()

            val text = File(dir, "desktop.properties").readText()
            assertTrue(text.contains("theme.dayNight=auto"))
            assertTrue(text.contains("theme.timezone=Europe/Athens"))
            assertTrue(text.contains("theme.autoTimezone=true"))
            assertTrue(text.contains("location.networkAssist=true"))
            assertTrue(text.contains("location.countryCode=GR"))
            assertFalse(text.contains("ip="))

            val loaded = TelosDesktopSettings(dir).also { it.load() }
            assertEquals("auto", loaded.themeDayNight)
            assertEquals("Europe/Athens", loaded.themeTimezone)
            assertTrue(loaded.themeAutoTimezone)
            assertTrue(loaded.locationNetworkAssist)
            assertEquals("GR", loaded.locationCountryCode)
            assertEquals(37.98, loaded.themeLatitude!!, 0.001)
            assertEquals(23.73, loaded.themeLongitude!!, 0.001)
            assertEquals("paper", loaded.themeIntensity)
        } finally {
            dir.deleteRecursively()
        }
    }
}
