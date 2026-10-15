package de.mm20.launcher2.applications

import org.junit.Assert.assertEquals
import org.junit.Test

class FrozenLauncherOrderTest {
    private data class Activity(val key: String, val enabled: Boolean)
    private fun ordered(rows: List<Activity>, known: Set<String> = emptySet()) =
        orderFrozenLaunchers(rows, known, { it.key }, { it.enabled }).map { it.key }
    @Test fun frozenAppKeepsPreviouslyAdvertisedComponent() {
        assertEquals(listOf("real", "seasonal"), ordered(listOf(Activity("seasonal", true), Activity("real", false)), setOf("real")))
    }
    @Test fun coldScanPrefersManifestEnabledLauncherOverDisabledAliases() {
        assertEquals(listOf("real", "seasonal"), ordered(listOf(Activity("seasonal", false), Activity("real", true))))
    }
    @Test fun equivalentCandidatesKeepTheirOriginalOrder() {
        assertEquals(listOf("one", "two"), ordered(listOf(Activity("one", true), Activity("two", true))))
    }
}
