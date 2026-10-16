package de.mm20.launcher2.network.vpn

import de.mm20.launcher2.network.api.NetworkSettingsValues
import de.mm20.launcher2.network.api.AppRule
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NetworkSettingsRestoreTest {
    @Test fun meteringRulesAreIndependentOfWifiOrMobileTransport() {
        assertTrue(AppRule(42, blockMetered = true).blocksMetering(true))
        assertFalse(AppRule(42, blockMetered = true).blocksMetering(false))
        assertTrue(AppRule(42, blockUnmetered = true).blocksMetering(false))
        assertFalse(AppRule(42, blockUnmetered = true).blocksMetering(true))
    }

    @Test fun oldSettingsKeepCompatibleDefaults() {
        val settings = Json.decodeFromString(NetworkSettingsValues.serializer(), """{"engineWanted":true,"torEnabled":true,"torAppIds":[10042]}""")
        assertTrue(settings.engineWanted)
        assertTrue(settings.torEnabled)
        assertEquals(setOf(10042), settings.torAppIds)
        assertEquals(settings, Json.decodeFromString(NetworkSettingsValues.serializer(), Json.encodeToString(NetworkSettingsValues.serializer(), settings)))
    }
    @Test fun oldRulesAndNewNotesSurviveRoundTrip() {
        val old = Json.decodeFromString(AppRule.serializer(), """{"appId":42,"blockWifi":true}""")
        assertFalse(old.blockMetered)
        val saved = old.copy(blockMetered = true, blockUnmetered = true, note = "Work profile")
        assertEquals(saved, Json.decodeFromString(AppRule.serializer(), Json.encodeToString(AppRule.serializer(), saved)))
    }
}
