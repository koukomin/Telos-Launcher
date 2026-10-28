package de.mm20.launcher2.ui.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivilegedWifiStoreTest {
    @Test fun importKeepsPasswordOnItsOwnNetwork() {
        val json = """[{"ssid":"Open","password":null},{"ssid":"Home","password":"secret"}]"""
        val rows = PrivilegedWifiStore.importJson(json)
        assertNull(rows.first { it.ssid == "Open" }.psk)
        assertEquals("secret", rows.first { it.ssid == "Home" }.psk)
    }

    @Test fun roundTripJsonPreservesSsidAndNote() {
        val original = listOf(WifiEntry("Cafe", "abc", note = "corner table"))
        val parsed = PrivilegedWifiStore.importJson(PrivilegedWifiStore.exportJson(original))
        assertEquals("Cafe", parsed.single().ssid)
        assertEquals("abc", parsed.single().psk)
        assertEquals("corner table", parsed.single().note)
    }
}
