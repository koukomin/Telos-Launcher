package de.mm20.launcher2.freeze

import org.junit.Assert.*
import org.junit.Test

class ScreenOffFreezePolicyTest {
    @Test fun appDelayWinsOverImmediateGlobalFreeze() {
        assertEquals(mapOf("a" to 600_000L, "b" to 0L),
            screenOffFreezeDelays(setOf("a", "b"), mapOf("a" to 10), true, true, 15))
    }
    @Test fun immediateOverrideWinsOverGlobalIdle() {
        assertEquals(mapOf("a" to 0L, "b" to 900_000L),
            screenOffFreezeDelays(setOf("a", "b"), mapOf("a" to 0), false, true, 15))
    }
    @Test fun overridesNeverOptInUnselectedApps() {
        assertEquals(emptyMap<String, Long>(), screenOffFreezeDelays(emptySet(), mapOf("a" to 5), true, true, 15))
    }
    @Test fun explicitAppScheduleWorksWithoutGlobalScreenTrigger() {
        assertEquals(mapOf("a" to 300_000L), screenOffFreezeDelays(setOf("a", "b"), mapOf("a" to 5), false, false, 15))
    }
    @Test fun inheritedAppHasNoDeadlineWhenProfileDisablesBothTriggers() {
        assertEquals(emptyMap<String, Long>(), screenOffFreezeDelays(setOf("a"), emptyMap(), false, false, 15))
    }
    @Test fun invalidImportedDelaysAreBounded() {
        assertEquals(mapOf("a" to 86_400_000L, "b" to 0L),
            screenOffFreezeDelays(setOf("a", "b"), mapOf("a" to Int.MAX_VALUE, "b" to -7), false, false, 15))
    }
    @Test fun screenOnDisableAndRebootInvalidatePendingPlan() {
        assertTrue(screenOffPlanIsValid(false, true, 7, 7))
        assertFalse(screenOffPlanIsValid(true, true, 7, 7))
        assertFalse(screenOffPlanIsValid(false, false, 7, 7))
        assertFalse(screenOffPlanIsValid(false, true, 7, 8))
    }
    @Test fun systemSafetyIsCaseInsensitiveAndFailClosed() {
        assertFalse(canFreezeSystemApp("Unsafe", false, true))
        assertFalse(canFreezeSystemApp("Expert", false, false))
        assertTrue(canFreezeSystemApp("EXPERT", false, true))
        assertFalse(canFreezeSystemApp("Recommended", true, true))
        assertTrue(canFreezeSystemApp(null, false, false))
    }
    @Test fun validCommunitySnapshotParsesAndRetainsRiskCategories() {
        val json = (0..1000).joinToString(prefix = "{", postfix = "}") { index ->
            "\"com.test.p$index\":{\"description\":\"Test\",\"removal\":\"${if (index == 0) "Unsafe" else "Recommended"}\"}"
        }
        val parsed = parseBloatDescriptions(json)
        assertEquals(1001, parsed.size)
        assertEquals("Unsafe", parsed["com.test.p0"]?.recommendation)
    }
    @Test(expected = IllegalArgumentException::class) fun incompleteDownloadCannotReplaceDatabase() {
        parseBloatDescriptions("{}")
    }
}
