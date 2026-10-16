package de.mm20.launcher2.comms.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferNetworkPolicyTest {
    @Test fun wifiOnlyAlsoRejectsUnmeteredMobile() {
        val p = TransferNetworkPolicy(mobile = false, roaming = false, metered = false)
        assertTrue(p.allows(true, true, false, false, false))
        assertFalse(p.allows(true, false, true, false, false))
        assertFalse(p.allows(true, false, true, false, true))
    }
    @Test fun meteredWifiAndRoamingAreIndependentRestrictions() {
        val p = TransferNetworkPolicy(roaming = false, metered = false)
        assertFalse(p.allows(true, true, false, false, true))
        assertTrue(p.copy(metered = true).allows(true, true, false, false, true))
        assertFalse(p.copy(metered = true).allows(true, false, true, true, true))
        assertTrue(p.copy(metered = true, roaming = true).allows(true, false, true, true, true))
    }
    @Test fun wifiMayBeBlockedWhileMobileIsAllowed() {
        val p = TransferNetworkPolicy(wifi = false)
        assertFalse(p.allows(true, true, false, false, false))
        assertTrue(p.allows(true, false, true, false, true))
    }
    @Test fun unknownVpnTransportFailsClosedUnderTransportRestrictions() {
        assertFalse(TransferNetworkPolicy(mobile = false).allows(true, false, false, false, false))
        assertTrue(TransferNetworkPolicy().allows(true, false, false, false, true))
        assertFalse(TransferNetworkPolicy().allows(false, true, false, false, false))
    }
}
