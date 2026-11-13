package com.telos.desktop.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeIntensityTest {
    @Test
    fun parseDefaultsToQuietOps() {
        assertEquals(ThemeIntensity.QuietOps, ThemeIntensity.parse(null))
        assertEquals(ThemeIntensity.QuietOps, ThemeIntensity.parse(""))
        assertEquals(ThemeIntensity.QuietOps, ThemeIntensity.parse("nope"))
    }

    @Test
    fun parseKnownAliases() {
        assertEquals(ThemeIntensity.QuietOps, ThemeIntensity.parse("quiet"))
        assertEquals(ThemeIntensity.QuietOps, ThemeIntensity.parse("Quiet_Ops"))
        assertEquals(ThemeIntensity.VaultNeon, ThemeIntensity.parse("neon"))
        assertEquals(ThemeIntensity.VaultNeon, ThemeIntensity.parse("vault_neon"))
        assertEquals(ThemeIntensity.PaperOps, ThemeIntensity.parse("paper"))
        assertEquals(ThemeIntensity.PaperOps, ThemeIntensity.parse("light"))
    }

    @Test
    fun propertyRoundTrip() {
        ThemeIntensity.entries.forEach { intensity ->
            assertEquals(intensity, ThemeIntensity.parse(intensity.propertyValue))
        }
    }

    @Test
    fun neonHasStrongerBorderThanQuiet() {
        assertTrue(ThemeIntensity.VaultNeon.techBorderAlpha > ThemeIntensity.QuietOps.techBorderAlpha)
    }
}
