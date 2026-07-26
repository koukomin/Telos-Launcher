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
        get() = dataStore.data.map { it.shutters.shuttersEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(shutters = it.shutters.copy(shuttersEnabled = enabled)) }
    }

    val apps
        get() = dataStore.data.map { it.shutters.shutterApps }.distinctUntilChanged()

    /** The SavableSearchable key assigned as [packageName]'s shutter, if any. */
    fun appFor(packageName: String) = dataStore.data
        .map { it.shutters.shutterApps[packageName] }
        .distinctUntilChanged()

    fun setApp(packageName: String, searchableKey: String?) {
        dataStore.update {
            it.copy(
                shutters = it.shutters.copy(
                    shutterApps = if (searchableKey != null) {
                        it.shutters.shutterApps + (packageName to searchableKey)
                    } else {
                        it.shutters.shutterApps - packageName
                    }
                )
            )
        }
    }
}
