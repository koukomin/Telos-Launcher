package de.mm20.launcher2.comms.telephony

import org.junit.Assert.*
import org.junit.Test

class CallerIdentityTest {
    @Test fun networkLookupRequiresACompletedUnknownLocalResult() {
        assertFalse(shouldLookupCaller(CallerIdentity(), null, false))
        assertFalse(shouldLookupCaller(CallerIdentity(localLookupComplete = true, localContactFound = true), null, false))
        val unknown = CallerIdentity(localLookupComplete = true)
        assertTrue(shouldLookupCaller(unknown, null, false))
        assertFalse(shouldLookupCaller(unknown, "Remote contact", false))
        assertFalse(shouldLookupCaller(unknown, null, true))
    }

    @Test fun storedNameWinsOverCarrierAndRemoteText() {
        assertEquals("Saved name", callerName(" Saved name ", "Carrier", "Remote"))
        assertEquals("Carrier", callerName("  ", "Carrier", "Remote"))
        assertEquals("Remote", callerName(null, "", "Remote"))
        assertNull(callerName(null, " ", null))
    }

    @Test fun staleNotificationCannotHangUpReplacementCall() {
        val active = InCallUiState(hasCall = true, active = true, actionToken = 22)
        assertTrue(callActionAllowed(active, 22, "hangup"))
        assertFalse(callActionAllowed(active, 21, "hangup"))
        assertFalse(callActionAllowed(active, 22, "answer"))
        assertFalse(callActionAllowed(active, 22, "reject"))
        assertFalse(callActionAllowed(active, 22, "unexpected"))
    }

    @Test fun answerAndRejectRequireTheCurrentIncomingCall() {
        val ringing = InCallUiState(hasCall = true, incoming = true, actionToken = 7)
        assertTrue(callActionAllowed(ringing, 7, "answer"))
        assertTrue(callActionAllowed(ringing, 7, "reject"))
        assertFalse(callActionAllowed(ringing, 7, "hangup"))
        assertFalse(callActionAllowed(ringing.copy(hasCall = false), 7, "answer"))
        assertFalse(callActionAllowed(ringing.copy(actionToken = 0), 0, "answer"))
    }
}
