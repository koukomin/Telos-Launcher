package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * "Shutters" (Action Launcher style): swipe up on an app icon to launch an app/shortcut assigned
 * to it, prompting for one on first use. Opt-in, off by default.
 */
class ShutterSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val enabled
        get() = dataStore.data.map { it.shuttersEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(shuttersEnabled = enabled) }
    }

    val apps
        get() = dataStore.data.map { it.shutterApps }.distinctUntilChanged()

    /** The SavableSearchable key assigned as [packageName]'s shutter, if any. */
    fun appFor(packageName: String) = dataStore.data
        .map { it.shutterApps[packageName] }
        .distinctUntilChanged()

    fun setApp(packageName: String, searchableKey: String?) {
        dataStore.update {
            it.copy(
                shutterApps = if (searchableKey != null) {
                    it.shutterApps + (packageName to searchableKey)
                } else {
                    it.shutterApps - packageName
                }
            )
        }
    }
}
