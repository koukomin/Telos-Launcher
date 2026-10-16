package de.mm20.launcher2.network.vpn

import org.junit.Assert.*
import org.junit.Test

class VpnRecoveryPolicyTest {
    @Test fun stickyRestartRestoresOnlyPriorIntent() {
        assertTrue(VpnRecoveryPolicy.shouldStart(null, true))
        assertFalse(VpnRecoveryPolicy.shouldStart(null, false))
        assertTrue(VpnRecoveryPolicy.shouldStart("android.net.VpnService", false))
        assertFalse(VpnRecoveryPolicy.shouldStart("de.mm20.launcher2.network.STOP", true))
    }
    @Test fun upgradeIsIndependentOfRebootPreference() {
        assertTrue(VpnRecoveryPolicy.restoreAfterBroadcast(false, true, false))
        assertFalse(VpnRecoveryPolicy.restoreAfterBroadcast(true, true, false))
        assertTrue(VpnRecoveryPolicy.restoreAfterBroadcast(true, true, true))
        assertFalse(VpnRecoveryPolicy.restoreAfterBroadcast(false, false, true))
    }
}
