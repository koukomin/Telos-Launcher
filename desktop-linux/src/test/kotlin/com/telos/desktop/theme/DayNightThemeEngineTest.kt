package com.telos.desktop.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DayNightThemeEngineTest {
    private val athens = ZoneId.of("Europe/Athens")
    private val utc = ZoneId.of("UTC")

    @Test
    fun parseZoneIdAcceptsIanaAndFallsBack() {
        assertEquals("Europe/Athens", DayNightThemeEngine.parseZoneId("Europe/Athens").id)
        assertEquals("UTC", DayNightThemeEngine.parseZoneId("not a/zone", fallback = utc).id)
        assertEquals(ZoneId.systemDefault().id, DayNightThemeEngine.parseZoneId("").id)
        assertEquals(ZoneId.systemDefault().id, DayNightThemeEngine.parseZoneId(null).id)
    }

    @Test
    fun dayNightModeParse() {
        assertEquals(DayNightMode.Off, DayNightMode.parse(null))
        assertEquals(DayNightMode.Off, DayNightMode.parse("off"))
        assertEquals(DayNightMode.Auto, DayNightMode.parse("auto"))
        assertEquals(DayNightMode.Light, DayNightMode.parse("light"))
        assertEquals(DayNightMode.Dark, DayNightMode.parse("dark"))
        assertEquals(DayNightMode.Light, DayNightMode.parse("paper"))
        assertEquals(DayNightMode.Dark, DayNightMode.parse("quiet"))
    }

    @Test
    fun approximateNightUses2000To0600() {
        assertTrue(DayNightThemeEngine.isApproximateNight(LocalTime.of(20, 0)))
        assertTrue(DayNightThemeEngine.isApproximateNight(LocalTime.of(23, 30)))
        assertTrue(DayNightThemeEngine.isApproximateNight(LocalTime.of(5, 59)))
        assertFalse(DayNightThemeEngine.isApproximateNight(LocalTime.of(6, 0)))
        assertFalse(DayNightThemeEngine.isApproximateNight(LocalTime.of(12, 0)))
        assertFalse(DayNightThemeEngine.isApproximateNight(LocalTime.of(19, 59)))
    }

    @Test
    fun autoWithoutCoordsUsesApproximateHours() {
        val day = ZonedDateTime.of(2026, 6, 15, 14, 0, 0, 0, athens).toInstant()
        val night = ZonedDateTime.of(2026, 6, 15, 22, 0, 0, 0, athens).toInstant()
        assertEquals(
            DayNightPhase.PaperOps,
            DayNightThemeEngine.resolvePhase(DayNightMode.Auto, day, athens),
        )
        assertEquals(
            DayNightPhase.QuietOps,
            DayNightThemeEngine.resolvePhase(DayNightMode.Auto, night, athens),
        )
    }

    @Test
    fun manualLightAndDarkOverrideClock() {
        val night = ZonedDateTime.of(2026, 6, 15, 23, 0, 0, 0, utc).toInstant()
        val day = ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, utc).toInstant()
        assertEquals(
            DayNightPhase.PaperOps,
            DayNightThemeEngine.resolvePhase(DayNightMode.Light, night, utc),
        )
        assertEquals(
            DayNightPhase.QuietOps,
            DayNightThemeEngine.resolvePhase(DayNightMode.Dark, day, utc),
        )
        assertEquals(
            DayNightPhase.QuietOps,
            DayNightThemeEngine.resolvePhase(DayNightMode.Off, day, utc),
        )
    }

    @Test
    fun solarWindowUsesLatLonWhenPresent() {
        // Athens ~37.98N 23.73E mid-June — daytime at 12:00 local
        val noon = ZonedDateTime.of(2026, 6, 21, 12, 0, 0, 0, athens).toInstant()
        val midnight = ZonedDateTime.of(2026, 6, 21, 1, 0, 0, 0, athens).toInstant()
        assertFalse(
            DayNightThemeEngine.isNight(noon, athens, latitude = 37.98, longitude = 23.73),
        )
        assertTrue(
            DayNightThemeEngine.isNight(midnight, athens, latitude = 37.98, longitude = 23.73),
        )
    }

    @Test
    fun millisUntilNextBoundaryIsBounded() {
        val ms = DayNightThemeEngine.millisUntilNextBoundary(
            instant = Instant.parse("2026-06-15T10:00:00Z"),
            zoneId = utc,
            minMs = 30_000L,
            maxMs = 15 * 60_000L,
        )
        assertTrue(ms in 30_000L..(15 * 60_000L))
    }
}
