package de.mm20.launcher2.preferences.ui

import org.junit.Assert.*
import org.junit.Test

class ShutterHostTest {
    @Test fun differentVirtualIconsDoNotShareShutters() {
        val phone = ShutterSettings.hostKey("telos_phone_app://phone", "com.dimitris.telos.release")
        val sms = ShutterSettings.hostKey("telos_messages_app://messages", "com.dimitris.telos.release")
        assertNotEquals(phone, sms)
        assertEquals("org.example", ShutterSettings.hostKey("app://org.example/Main", "org.example"))
    }
}
