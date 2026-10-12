package de.mm20.launcher2.comms.tv

import de.mm20.launcher2.comms.tv.TvReconnectDecision.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvReconnectDecisionTest {
    @Test fun offlineAlwaysWaits() {
        assertEquals(Action.WAIT_FOR_NETWORK, TvReconnectDecision.decide(false, false, 0, 3))
        assertEquals(Action.WAIT_FOR_NETWORK, TvReconnectDecision.decide(false, true, 99, 3))
    }

    @Test fun onlineStreamThatPlayedRetriesThenFailsOver() {
        assertEquals(Action.RETRY_SAME, TvReconnectDecision.decide(true, true, 0, 3))
        assertEquals(Action.RETRY_SAME, TvReconnectDecision.decide(true, true, 2, 3))
        assertEquals(Action.FAIL_OVER, TvReconnectDecision.decide(true, true, 3, 3))
    }

    @Test fun onlineStreamThatNeverPlayedFailsOver() {
        assertEquals(Action.FAIL_OVER, TvReconnectDecision.decide(true, false, 0, 3))
    }

    @Test fun onlineDetection() {
        assertTrue(TvReconnectDecision.isOnline(true, true, true))
        assertFalse(TvReconnectDecision.isOnline(false, true, true))
        assertFalse(TvReconnectDecision.isOnline(true, false, true))
        assertFalse(TvReconnectDecision.isOnline(true, true, false))
    }

    @Test fun errorOnlyWhileOnline() {
        assertTrue(TvReconnectDecision.giveUpAsError(true))
        assertFalse(TvReconnectDecision.giveUpAsError(false))
    }
}
