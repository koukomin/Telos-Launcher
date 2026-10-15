package de.mm20.launcher2.preferences

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FrozenAppRestoreTest {
    @Test fun deviceLocalJournalRoundTripsAllProtectionModesAndLeaseIdentity() {
        val entries = mapOf("0:example.app" to FrozenAppRestore("example.app", 0, true, 3, true,
            mapOf("example.app/example.app.Main" to 2), true, "Root", "00000000-1111-2222-3333-444444444444"))
        assertEquals(entries, Json.decodeFromString<Map<String, FrozenAppRestore>>(Json.encodeToString(entries)))
    }
}
