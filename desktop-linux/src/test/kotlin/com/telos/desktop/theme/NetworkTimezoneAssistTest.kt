package com.telos.desktop.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTimezoneAssistTest {
    @Test
    fun optInGateRequiresBothFlags() {
        assertFalse(NetworkTimezoneAssist.isNetworkAllowed(false, false))
        assertFalse(NetworkTimezoneAssist.isNetworkAllowed(true, false))
        assertFalse(NetworkTimezoneAssist.isNetworkAllowed(false, true))
        assertTrue(NetworkTimezoneAssist.isNetworkAllowed(true, true))
    }

    @Test
    fun discoverWithoutOptInIsPreferenceOnlyAndDoesNotCallFetcher() {
        var called = false
        val result = NetworkTimezoneAssist.discover(
            NetworkTimezoneAssist.SettingsSnapshot(
                autoTimezone = false,
                networkAssist = false,
            ),
            fetcher = NetworkTimezoneAssist.JsonFetcher {
                called = true
                Result.success("""{"ip":"1.2.3.4","timezone":"Europe/Athens"}""")
            },
            killSwitchReader = { false },
        )
        assertEquals(NetworkTimezoneAssist.AssistStatus.PreferenceOnly, result.status)
        assertFalse(called)
        assertNull(result.hint)
    }

    @Test
    fun discoverWithKillSwitchIsPreferenceOnly() {
        var called = false
        val result = NetworkTimezoneAssist.discover(
            NetworkTimezoneAssist.SettingsSnapshot(
                autoTimezone = true,
                networkAssist = true,
                killSwitchEnabled = true,
            ),
            fetcher = NetworkTimezoneAssist.JsonFetcher {
                called = true
                Result.success("{}")
            },
            killSwitchReader = { true },
        )
        assertEquals(NetworkTimezoneAssist.AssistStatus.PreferenceOnly, result.status)
        assertFalse(called)
    }

    @Test
    fun discoverMockGeoAppliesTimezoneAndCountryWithoutIp() {
        val json = """
            {"ip":"203.0.113.50","city":"Athens","region":"Attica",
             "country_code":"GR","timezone":"Europe/Athens",
             "latitude":37.98,"longitude":23.73}
        """.trimIndent()
        val result = NetworkTimezoneAssist.discover(
            NetworkTimezoneAssist.SettingsSnapshot(
                autoTimezone = true,
                networkAssist = true,
            ),
            fetcher = NetworkTimezoneAssist.JsonFetcher { Result.success(json) },
            killSwitchReader = { false },
        )
        assertEquals(NetworkTimezoneAssist.AssistStatus.Applied, result.status)
        val hint = result.hint!!
        assertEquals("Europe/Athens", hint.timezoneId)
        assertEquals("GR", hint.countryCode)
        assertEquals(37.98, hint.latitude!!, 0.001)
        assertEquals(23.73, hint.longitude!!, 0.001)
        // Ensure IP never appears in the returned hint structure.
        assertFalse(hint.toString().contains("203.0.113"))
    }

    @Test
    fun discoverNetworkFailureIsFailed() {
        val result = NetworkTimezoneAssist.discover(
            NetworkTimezoneAssist.SettingsSnapshot(
                autoTimezone = true,
                networkAssist = true,
            ),
            fetcher = NetworkTimezoneAssist.JsonFetcher { Result.failure(Exception("offline")) },
            killSwitchReader = { false },
        )
        assertEquals(NetworkTimezoneAssist.AssistStatus.Failed, result.status)
    }

    @Test
    fun parseGeoJsonIgnoresIpField() {
        val hint = NetworkTimezoneAssist.parseGeoJson(
            """{"ip":"8.8.8.8","timezone":"America/New_York","country_code":"US","lat":40.7,"lon":-74.0}""",
        )!!
        assertEquals("America/New_York", hint.timezoneId)
        assertEquals("US", hint.countryCode)
        assertFalse(hint.toString().contains("8.8.8.8"))
    }
}
