package de.mm20.launcher2.freeze

import org.junit.Assert.*
import org.junit.Test

class RecentsFreezePolicyTest {
    @Test fun removalStartDoesNotFreezeATaskStillInRecents() {
        val task = RecentsTask(1, "com.test.app", 0)
        assertFalse(removedLastTask(task, listOf(task), 0))
    }
    @Test fun lastTaskRemovalCanRefreezeCurrentUserApp() {
        assertTrue(removedLastTask(RecentsTask(1, "com.test.app", 0), emptyList(), 0))
    }
    @Test fun anotherTaskOfSameAppPreventsFreeze() {
        assertFalse(removedLastTask(RecentsTask(1, "com.test.app", 0), listOf(RecentsTask(2, "com.test.app", 0)), 0))
    }
    @Test fun removalInWorkProfileNeverFreezesPersonalApp() {
        assertFalse(removedLastTask(RecentsTask(1, "com.test.app", 10), emptyList(), 0))
    }
    @Test fun unrelatedTaskDoesNotHoldRemovedApp() {
        assertTrue(removedLastTask(RecentsTask(1, "com.test.app", 0), listOf(RecentsTask(2, "com.test.other", 0)), 0))
    }
    @Test fun samePackageInAnotherProfileDoesNotHoldPersonalTask() {
        assertTrue(removedLastTask(RecentsTask(1, "com.test.app", 0), listOf(RecentsTask(2, "com.test.app", 10)), 0))
    }
}
