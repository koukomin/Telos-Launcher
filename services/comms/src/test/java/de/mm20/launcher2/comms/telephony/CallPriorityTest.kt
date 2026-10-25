package de.mm20.launcher2.comms.telephony

import org.junit.Assert.*
import org.junit.Test

class CallPriorityTest {
    @Test fun endedAndDisconnectingCallsCannotWinOverLiveCalls() {
        assertTrue(callPriority(4) < callPriority(7))
        assertTrue(callPriority(1) < callPriority(10))
        assertTrue(callPriority(3) < callPriority(7))
    }
    @Test fun ringingThenActiveThenConnectingThenHeld() {
        assertTrue(callPriority(2) < callPriority(4))
        assertTrue(callPriority(4) < callPriority(9))
        assertTrue(callPriority(9) < callPriority(3))
    }
}
