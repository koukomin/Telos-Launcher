package de.mm20.launcher2.network.api

import org.junit.Assert.*
import org.junit.Test

class FirewallCandidateTest {
    private val offlineSystem = AppEntry(1000, "android", "Android",
        packages = listOf("android", "system.sibling"), isSystem = true, hasInternet = false)

    @Test fun offlineSystemPackageOpensFromSystemApps() {
        assertTrue(offlineSystem.isFirewallCandidate("android"))
        assertTrue(offlineSystem.isFirewallCandidate("system.sibling"))
        assertFalse(offlineSystem.isFirewallCandidate("other.package"))
    }

    @Test fun generalFirewallStillOmitsAppsWithoutInternet() {
        assertFalse(offlineSystem.isFirewallCandidate(null))
        assertTrue(offlineSystem.copy(hasInternet = true).isFirewallCandidate(null))
    }
}
