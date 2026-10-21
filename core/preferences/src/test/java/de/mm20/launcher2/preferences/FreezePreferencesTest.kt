package de.mm20.launcher2.preferences

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FreezePreferencesTest {
    @Test fun upgradingOldPreferencesDoesNotEnableCompanionRules() {
        val old = Json.decodeFromString<FreezeGroup>("""{"freezeCompanionExtras":{"com.example.app":"com.example.helper"}}""")
        assertTrue(old.freezeBloatAutoUpdate)
        assertTrue(old.freezeCompanionEnabled.isEmpty())
        assertTrue(old.freezeScreenOffDelays.isEmpty())
        assertTrue(old.freezeScreenOffPending.isEmpty())
        assertEquals("com.example.helper", old.freezeCompanionExtras["com.example.app"])
    }
    @Test fun sharedCompanionIsOnlyReleasedAfterItsLastMaster() {
        val legacy = mapOf("com.example.helper" to "com.example.first")
        val shared = de.mm20.launcher2.preferences.freeze.addCompanionOwners(legacy,
            "com.example.second", listOf("com.example.helper"))
        val (remaining, released) = de.mm20.launcher2.preferences.freeze.removeCompanionOwner(shared, "com.example.first")
        assertTrue(released.isEmpty())
        assertEquals("com.example.second", remaining["com.example.helper"])
        val (empty, lastReleased) = de.mm20.launcher2.preferences.freeze.removeCompanionOwner(remaining, "com.example.second")
        assertTrue(empty.isEmpty())
        assertEquals(setOf("com.example.helper"), lastReleased)
    }
    @Test fun disabledDatabaseUpdatesRemainDisabledAfterRoundTrip() {
        val off = FreezeGroup(freezeBloatAutoUpdate = false)
        assertFalse(Json.decodeFromString<FreezeGroup>(Json.encodeToString(FreezeGroup.serializer(), off)).freezeBloatAutoUpdate)
    }
    @Test fun perAppOverridesAndExplicitRulesRoundTrip() {
        val original = FreezeGroup(freezeScreenOffDelays = mapOf("a" to 0, "b" to 15),
            freezeCompanionEnabled = setOf("a"), freezeCompanionExtras = mapOf("a" to "b,c"))
        assertEquals(original, Json.decodeFromString<FreezeGroup>(Json.encodeToString(FreezeGroup.serializer(), original)))
    }
}
