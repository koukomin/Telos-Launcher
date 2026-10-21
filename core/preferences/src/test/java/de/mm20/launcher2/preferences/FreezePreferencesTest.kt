package de.mm20.launcher2.preferences

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FreezePreferencesTest {
    @Test fun upgradingOldPreferencesDoesNotEnableCompanionRules() {
        val old = Json.decodeFromString<FreezeGroup>("""{"freezeCompanionExtras":{"com.example.app":"com.example.helper"}}""")
        assertTrue(old.freezeCompanionEnabled.isEmpty())
        assertTrue(old.freezeScreenOffDelays.isEmpty())
        assertTrue(old.freezeScreenOffPending.isEmpty())
        assertEquals("com.example.helper", old.freezeCompanionExtras["com.example.app"])
    }
    @Test fun perAppOverridesAndExplicitRulesRoundTrip() {
        val original = FreezeGroup(freezeScreenOffDelays = mapOf("a" to 0, "b" to 15),
            freezeCompanionEnabled = setOf("a"), freezeCompanionExtras = mapOf("a" to "b,c"))
        assertEquals(original, Json.decodeFromString<FreezeGroup>(Json.encodeToString(FreezeGroup.serializer(), original)))
    }
}
