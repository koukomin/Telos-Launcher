package de.mm20.launcher2.ui.system

import org.junit.Assert.*
import org.junit.Test

/** Android ICU rejects an unescaped closing brace which the desktop JVM accepts. */
class SavedWifiAndroidTest {
    @Test fun qrEscapesWifiDelimiters() {
        assertEquals("WIFI:T:WPA;S:A\\;B;P:C\\:D;;", wifiQrPayload("A;B", "C:D"))
    }
    @Test fun parserInitializesOnAndroidAndKeepsCredentialsInTheirOwnNetwork() {
        assertTrue(SavedWifiParser.parse("").isEmpty())
        val rows = SavedWifiParser.parse("""
            network={
              ssid="Open"
            }
            network={
              ssid="Private"
              psk="test-password"
            }
        """.trimIndent())
        assertEquals(listOf(WifiEntry("Open", null), WifiEntry("Private", "test-password")), rows)
    }
}
