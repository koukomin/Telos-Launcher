package de.mm20.launcher2.comms.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvBackgroundPolicyTest {
    @Test fun pausesOnlyWhenNotAllowed() {
        assertTrue(TvBackgroundPolicy.pauseWhenLeaving(false))
        assertFalse(TvBackgroundPolicy.pauseWhenLeaving(true))
    }

    @Test fun videoOffOnlyInBackgroundWhenAllowed() {
        assertTrue(TvBackgroundPolicy.videoDisabled(true, true))
        assertFalse(TvBackgroundPolicy.videoDisabled(true, false))
        assertFalse(TvBackgroundPolicy.videoDisabled(false, true))
    }

    @Test fun idleStop() {
        val m = TvBackgroundPolicy.IDLE_STOP_MS
        assertFalse(TvBackgroundPolicy.shouldStopAfterIdle(false, false, 1000, 1000 + m - 1))
        assertTrue(TvBackgroundPolicy.shouldStopAfterIdle(false, false, 1000, 1000 + m))
        assertFalse(TvBackgroundPolicy.shouldStopAfterIdle(true, false, 1000, 1000 + m))
        assertFalse(TvBackgroundPolicy.shouldStopAfterIdle(false, true, 1000, 1000 + m))
        assertFalse(TvBackgroundPolicy.shouldStopAfterIdle(false, false, -1, 1000 + m))
    }

    @Test fun noIdleStopWhileReconnecting() {
        val m = TvBackgroundPolicy.IDLE_STOP_MS
        assertFalse(TvBackgroundPolicy.shouldStopAfterIdle(false, false, 1000, 1000 + m, reconnecting = true))
        assertTrue(TvBackgroundPolicy.shouldStopAfterIdle(false, false, 1000, 1000 + m, reconnecting = false))
    }

    @Test fun notificationPermission() {
        assertTrue(TvBackgroundPolicy.needsNotificationPermission(33, false))
        assertFalse(TvBackgroundPolicy.needsNotificationPermission(33, true))
        assertFalse(TvBackgroundPolicy.needsNotificationPermission(32, false))
    }
}
