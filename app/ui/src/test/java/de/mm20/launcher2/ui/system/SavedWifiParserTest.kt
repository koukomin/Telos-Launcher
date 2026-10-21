package de.mm20.launcher2.ui.system

import org.junit.Assert.*
import org.junit.Test

class SavedWifiParserTest {
    @Test fun openNetworkCannotBorrowTheNextNetworksPassword() {
        val raw = """<Network><WifiConfiguration><string name="SSID">&quot;Open&quot;</string><null name="PreSharedKey" /></WifiConfiguration></Network>
            <Network><WifiConfiguration><string name="SSID">&quot;Private&quot;</string><string name="PreSharedKey">&quot;a&amp;b&quot;</string></WifiConfiguration></Network>"""
        val networks = SavedWifiParser.parse(raw)
        assertNull(networks.first { it.ssid == "Open" }.psk)
        assertEquals("a&b", networks.first { it.ssid == "Private" }.psk)
    }
    @Test fun legacyNetworksRemainScopedAndMaskedValuesAreNotSecrets() {
        val raw = """network={
            ssid="Open"
            }
            network={
            ssid="Private"
            psk="test-only-password"
            }
        """
        val networks = SavedWifiParser.parse(raw)
        assertNull(networks.first().psk)
        assertEquals("test-only-password", networks.last().psk)
        assertNull(SavedWifiParser.parse("""<Network><string name="SSID">Masked</string><string name="PreSharedKey">*</string></Network>""").single().psk)
    }
}
