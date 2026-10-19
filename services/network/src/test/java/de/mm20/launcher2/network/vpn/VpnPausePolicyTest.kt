package de.mm20.launcher2.network.vpn

import org.junit.Assert.*
import org.junit.Test

class VpnPausePolicyTest {
    @Test fun onlyActiveUserIntentCanResume() {
        assertTrue(VpnPausePolicy.pending(true, 2000, 1000))
        assertFalse(VpnPausePolicy.pending(false, 2000, 1000))
        assertFalse(VpnPausePolicy.pending(true, 1000, 1000))
        assertFalse(VpnPausePolicy.pending(true, 0, 1000))
    }
    @Test fun rebootHonorsOptInWithoutConfusingProcessRestart() {
        assertTrue(VpnPausePolicy.rebootCancels(10, 11, false))
        assertFalse(VpnPausePolicy.rebootCancels(10, 11, true))
        assertFalse(VpnPausePolicy.rebootCancels(10, 10, false))
        assertFalse(VpnPausePolicy.rebootCancels(-1, 11, false))
    }
    @Test fun choicesMatchRequestedDurations() {
        assertEquals(listOf(300_000L, 1_800_000L, 3_600_000L, 7_200_000L), VpnPausePolicy.durationsMs)
    }
}
