package de.mm20.launcher2.appmanagement.hardware

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelosContextHardwareEngineTest {
    @Test
    fun whatsappGetsCameraAndMic() {
        val p = TelosContextHardwareEngine.defaultSeedProfile("com.whatsapp", "WhatsApp")
        assertTrue(p.camera)
        assertTrue(p.microphone)
        assertFalse(p.location)
    }

    @Test
    fun mapsGetsGps() {
        val p = TelosContextHardwareEngine.defaultSeedProfile("com.google.android.apps.maps", "Maps")
        assertTrue(p.location)
        assertFalse(p.camera)
    }

    @Test
    fun dialerGetsMic() {
        val p = TelosContextHardwareEngine.defaultSeedProfile("com.google.android.dialer", "Phone")
        assertTrue(p.microphone)
        assertFalse(p.camera)
    }

    @Test
    fun unknownAppGetsNothing() {
        val p = TelosContextHardwareEngine.defaultSeedProfile("org.example.notes", "Notes")
        assertFalse(p.any)
    }
}
