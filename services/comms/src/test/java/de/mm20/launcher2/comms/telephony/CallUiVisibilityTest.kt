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
}
