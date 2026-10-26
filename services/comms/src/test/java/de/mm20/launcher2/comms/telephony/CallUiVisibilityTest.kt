package de.mm20.launcher2.comms.telephony

import org.junit.Assert.*
import org.junit.Test

class CallUiVisibilityTest {
    @Test fun fullScreenAndSmallCallNotificationAreMutuallyExclusive() {
        assertTrue(showCallNotification(true, false))
        assertFalse(showCallNotification(true, true))
        assertFalse(showCallNotification(false, false))
    }
    @Test fun overlappingActivitiesCannotClearEachOthersVisibility() {
        val first = Any(); val replacement = Any()
        try {
            CallUiVisibility.setVisible(first, true)
            CallUiVisibility.setVisible(replacement, true)
            CallUiVisibility.setVisible(first, false)
            assertTrue(CallUiVisibility.visible.value)
            CallUiVisibility.setVisible(replacement, false)
            assertFalse(CallUiVisibility.visible.value)
        } finally {
            CallUiVisibility.setVisible(first, false)
            CallUiVisibility.setVisible(replacement, false)
        }
    }

    @Test fun answerTransitionNeverNeedsAnOngoingCallStyleOrFullScreenIntent() {
        val ringing = InCallUiState(hasCall = true, incoming = true, actionToken = 7)
        assertEquals(CallNotificationKind.IncomingFullScreen, callNotificationKind(ringing, false, true))
        assertEquals(CallNotificationKind.IncomingActions, callNotificationKind(ringing, false, false))
        val active = ringing.copy(incoming = false, active = true)
        assertEquals(CallNotificationKind.OngoingActions, callNotificationKind(active, false, true))
        assertEquals(CallNotificationKind.None, callNotificationKind(active, true, false))
        assertFalse(currentNotificationState(ringing, active))
        assertFalse(currentNotificationState(active, InCallUiState()))
        assertFalse(currentNotificationState(active, active.copy(actionToken = 8)))
        assertTrue(currentNotificationState(active, active))
    }

    @Test fun startupCleanupRequiresBothTelecomAndLocalSessionToBeIdle() {
        assertTrue(clearOrphanCallNotification(false, false))
        assertFalse(clearOrphanCallNotification(true, false))
        assertFalse(clearOrphanCallNotification(false, true))
        assertFalse(clearOrphanCallNotification(null, false))
    }
}
