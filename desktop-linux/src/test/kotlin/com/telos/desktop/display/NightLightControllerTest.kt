package com.telos.desktop.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightLightControllerTest {
    @Test
    fun clampsTemperature() {
        assertEquals(1000, NightLightController.clampTemp(50))
        assertEquals(6500, NightLightController.clampTemp(9000))
        assertEquals(4500, NightLightController.clampTemp(4500))
    }

    @Test
    fun applyWithoutToolsIsPreferenceOnlyOrUsesHostTool() {
        val status = NightLightController.apply(enabled = true, temperatureKelvin = 4200)
        // CI hosts usually lack gammastep/redshift/wlsunset → PreferenceOnly.
        // If a tool is installed, Applied/Failed are also valid.
        assertTrue(
            status == NightLightController.ApplyStatus.PreferenceOnly ||
                status == NightLightController.ApplyStatus.Applied ||
                status == NightLightController.ApplyStatus.Failed,
        )
        if (NightLightController.detectBackend() == NightLightController.Backend.None) {
            assertEquals(NightLightController.ApplyStatus.PreferenceOnly, status)
        }
    }

    @Test
    fun snapshotReportsBackendHonestly() {
        val snap = NightLightController.snapshot(enabled = false, temperatureKelvin = 99999)
        assertEquals(6500, snap.temperatureKelvin)
        assertEquals(false, snap.enabled)
        if (snap.backend == NightLightController.Backend.None) {
            assertEquals(NightLightController.ApplyStatus.PreferenceOnly, snap.status)
            assertEquals(null, snap.toolPath)
        }
    }
}
