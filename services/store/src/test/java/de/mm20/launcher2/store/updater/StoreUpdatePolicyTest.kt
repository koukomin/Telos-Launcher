package de.mm20.launcher2.store.updater

import de.mm20.launcher2.store.options.ItemOptions
import org.junit.Assert.*
import org.junit.Test

class StoreUpdatePolicyTest {
    @Test fun excludesPlayButAllowsOtherStoresAndSideloads() {
        assertFalse(StoreUpdatePolicy.discoverInstaller("com.android.vending"))
        assertTrue(StoreUpdatePolicy.discoverInstaller("org.fdroid.fdroid"))
        assertTrue(StoreUpdatePolicy.discoverInstaller("com.aurora.store"))
        assertTrue(StoreUpdatePolicy.discoverInstaller(null))
    }
    @Test fun discoveredAppsRequireTheirOwnOptInEvenWhenGlobalIsOn() {
        assertFalse(StoreUpdatePolicy.automatic(ItemOptions(autoUpdate = false), true))
        assertTrue(StoreUpdatePolicy.automatic(ItemOptions(autoUpdate = true), false))
        assertFalse(StoreUpdatePolicy.automatic(ItemOptions(), false))
        assertTrue(StoreUpdatePolicy.automatic(ItemOptions(), true))
    }
    @Test fun optInDoesNotOverrideTrackingPinningOrBackgroundExclusion() {
        assertFalse(StoreUpdatePolicy.automatic(ItemOptions(autoUpdate = true, trackOnly = true), true))
        assertFalse(StoreUpdatePolicy.automatic(ItemOptions(autoUpdate = true, pinned = true), true))
        assertFalse(StoreUpdatePolicy.automatic(ItemOptions(autoUpdate = true, excludeFromBackground = true), true))
    }
}
