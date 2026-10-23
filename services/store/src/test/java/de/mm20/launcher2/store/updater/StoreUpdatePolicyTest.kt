package de.mm20.launcher2.store.updater

import de.mm20.launcher2.store.options.ItemOptions
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import org.junit.Assert.*
import org.junit.Test

class StoreUpdatePolicyTest {
    @Test fun excludesPlayButAllowsOtherStoresAndSideloads() {
        assertFalse(StoreUpdatePolicy.discoverInstaller("com.android.vending"))
        assertTrue(StoreUpdatePolicy.discoverInstaller("org.fdroid.fdroid"))
        assertTrue(StoreUpdatePolicy.discoverInstaller("com.aurora.store"))
        assertTrue(StoreUpdatePolicy.discoverInstaller(null))
    }
    @Test fun globalDefaultAndPerAppOptOut() {
        assertFalse(StoreUpdatePolicy.automatic(ItemOptions(autoUpdate = false), true))
        assertFalse(StoreUpdatePolicy.automatic(ItemOptions(autoUpdate = true), false))
        assertFalse(StoreUpdatePolicy.automatic(ItemOptions(), false))
        assertTrue(StoreUpdatePolicy.automatic(ItemOptions(), true))
    }
    @Test fun perAppUpdateChecksAreEnabledByDefaultAndCanBeDisabled() {
        assertTrue(StoreUpdatePolicy.checksUpdates(ItemOptions()))
        assertFalse(StoreUpdatePolicy.checksUpdates(ItemOptions(checkForUpdates = false)))
    }
    @Test fun identifiesTheLauncherByIdPackageOrReleaseSource() {
        val launcher = StoreItem(
            id = "launcher-source",
            packageName = "de.mm20.launcher2.release",
            displayName = "Telos",
            source = AppSource.GitHub("Koukomin", "telos-launcher"),
        )
        assertTrue(StoreUpdatePolicy.isTelosLauncher(launcher, "de.mm20.launcher2"))
        assertTrue(StoreUpdatePolicy.isTelosLauncher(launcher.copy(id = "telos-self", source = AppSource.DirectApk("https://example.org/app.apk")), "de.mm20.launcher2"))
        assertTrue(StoreUpdatePolicy.isTelosLauncher(launcher.copy(packageName = "de.mm20.launcher2"), "de.mm20.launcher2"))
        assertFalse(StoreUpdatePolicy.isTelosLauncher(launcher.copy(source = AppSource.GitHub("example", "app")), "de.mm20.launcher2"))
    }
    @Test fun newInstallsEnableAutomaticUpdatesByDefault() {
        assertTrue(de.mm20.launcher2.store.options.GlobalOptions().autoInstall)
        assertTrue(de.mm20.launcher2.store.options.GlobalOptions().parallelUpdates)
        val saved = kotlinx.serialization.json.Json.decodeFromString(
            de.mm20.launcher2.store.options.GlobalOptions.serializer(), """{"autoInstall":false}""",
        )
        assertFalse(saved.autoInstall)
        assertTrue(saved.parallelUpdates)
    }
    @Test fun optInDoesNotOverrideTrackingPinningOrBackgroundExclusion() {
        assertFalse(StoreUpdatePolicy.automatic(ItemOptions(autoUpdate = true, trackOnly = true), true))
        assertFalse(StoreUpdatePolicy.automatic(ItemOptions(autoUpdate = true, pinned = true), true))
        assertFalse(StoreUpdatePolicy.automatic(ItemOptions(autoUpdate = true, excludeFromBackground = true), true))
    }
}
