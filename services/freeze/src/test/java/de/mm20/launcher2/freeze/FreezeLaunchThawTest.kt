package de.mm20.launcher2.freeze

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeLaunchThawTest {
    @Test fun runningEnabledAppStartsWithoutThaw() {
        assertFalse(needsLaunchThaw(enabled = true, suspended = false, stopped = false))
    }

    @Test fun disabledSuspendedOrForceStoppedAppsNeedThaw() {
        assertTrue(needsLaunchThaw(enabled = false, suspended = false, stopped = false))
        assertTrue(needsLaunchThaw(enabled = true, suspended = true, stopped = false))
        assertTrue(needsLaunchThaw(enabled = true, suspended = false, stopped = true))
    }
}
