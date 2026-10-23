package de.mm20.launcher2.network

import de.mm20.launcher2.network.api.PrivateDnsMode
import de.mm20.launcher2.network.api.NetworkSettingsValues
import de.mm20.launcher2.network.api.PrivateDnsState
import de.mm20.launcher2.network.api.WifiDnsRule
import de.mm20.launcher2.network.api.WifiDnsRules
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiDnsRulesTest {
    @Test
    fun normalizesQuotedSsidAndRejectsUnavailableOrInvalidValues() {
        assertEquals("Home Wi-Fi", WifiDnsRules.normalizeSsid("\"Home Wi-Fi\""))
        assertNull(WifiDnsRules.normalizeSsid(null))
        assertNull(WifiDnsRules.normalizeSsid("\"<unknown ssid>\""))
        assertNull(WifiDnsRules.normalizeSsid("02:00:00:00:00:00"))
        assertNull(WifiDnsRules.normalizeSsid("bad\u0000ssid"))
        assertNull(WifiDnsRules.normalizeSsid("x".repeat(33)))
    }

    @Test
    fun matchesOnlyExactNormalizedSsid() {
        val rule = WifiDnsRule("Home Wi-Fi", PrivateDnsMode.AUTOMATIC)

        assertEquals(rule, WifiDnsRules.matching(listOf(rule), "\"Home Wi-Fi\""))
        assertNull(WifiDnsRules.matching(listOf(rule), "Home"))
        assertNull(WifiDnsRules.matching(listOf(rule), null))
    }

    @Test
    fun automationRulesAndRestoreStateSurviveSettingsSerialization() {
        val values = NetworkSettingsValues(
            wifiDnsAutomationEnabled = true,
            wifiDnsRules = listOf(
                WifiDnsRule("Home Wi-Fi", PrivateDnsMode.PROVIDER, "dns.example"),
            ),
            wifiDnsBaseline = PrivateDnsState(PrivateDnsMode.AUTOMATIC),
            wifiDnsLastApplied = PrivateDnsState(PrivateDnsMode.PROVIDER, "dns.example"),
        )

        val encoded = Json.encodeToString(NetworkSettingsValues.serializer(), values)
        val decoded = Json.decodeFromString(NetworkSettingsValues.serializer(), encoded)

        assertEquals(values, decoded)
    }
}
