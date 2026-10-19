package de.mm20.launcher2.network.tor

import org.junit.Assert.*
import org.junit.Test

class TorPolicyTest {
    @Test fun globalTorCapturesUnassignedAppsAndUnknownOwners() {
        assertTrue(TorPolicy.required(10124, "example.org", emptyList(), emptySet(), true))
        // Inbound connections have no DNS name: global Tor must still reject direct ingress.
        assertTrue(TorPolicy.required(10124, null, emptyList(), emptySet(), true))
        assertTrue(TorPolicy.required(-1, "example.org", emptyList(), emptySet(), true))
        assertFalse(TorPolicy.required(10124, "example.org", emptyList(), emptySet(), false))
        assertFalse(TorPolicy.mayConnect(true, false, 6))
        // Malformed/imported settings may retain global intent with Tor disabled: still drop.
        assertFalse(TorPolicy.mayConnect(false, true, 6))
        assertFalse(TorPolicy.mayConnect(true, true, 17))
    }
    @Test fun bootstrapControlRepliesExposeActualProgress() {
        assertEquals(100, TorPolicy.bootstrapProgress("NOTICE BOOTSTRAP PROGRESS=100 TAG=done SUMMARY=Done"))
        assertEquals(45, TorPolicy.bootstrapProgress("NOTICE BOOTSTRAP PROGRESS=45 TAG=loading"))
        assertEquals(0, TorPolicy.bootstrapProgress(null))
        assertEquals(0, TorPolicy.bootstrapProgress("PROGRESS=1000"))
    }

    @Test fun onionNeverUsesPublicDnsEvenWhenDisabled() {
        assertTrue(TorPolicy.required(-1, "site.ONION.", emptyList(), emptySet()))
        assertFalse(TorPolicy.isOnion("onion.example.org"))
        assertFalse(TorPolicy.isOnion("notonion"))
        assertFalse(TorPolicy.mayConnect(false, true, 6))
    }
    @Test fun virtualAddressesCannotEscapeThroughDirectRouting() {
        assertTrue(TorPolicy.required(-1, null, listOf("10.192.0.1"), emptySet()))
        assertTrue(TorPolicy.isVirtual("fd87:d87e:eb43::42"))
        assertFalse(TorPolicy.isVirtual("10.191.255.255"))
        assertFalse(TorPolicy.isVirtual("fd87:d87e:eb44::1"))
        assertFalse(TorPolicy.isVirtual("example.org"))
    }
    @Test fun appIdsIncludeSecondaryProfilesAndSharedUids() {
        assertTrue(TorPolicy.required(1010123, "example.org", emptyList(), setOf(10123)))
        // Older Android versions may not identify the owner: route through Tor conservatively.
        assertTrue(TorPolicy.required(-1, "example.org", emptyList(), setOf(99999)))
        assertFalse(TorPolicy.required(-1, "example.org", emptyList(), emptySet()))
        assertTrue(TorPolicy.required(10123, "example.org", emptyList(), setOf(10123)))
        assertFalse(TorPolicy.required(10124, "example.org", emptyList(), setOf(10123)))
    }
    @Test fun noUdpOrBootstrapFallback() {
        assertFalse(TorPolicy.mayConnect(true, true, 17))
        assertFalse(TorPolicy.mayConnect(true, false, 6))
        assertTrue(TorPolicy.mayConnect(true, true, 6))
    }
}
