package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.ShutterWidgetRef
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * "Shutters" (Action Launcher style): swipe up on an app icon to reveal that app's assigned
 * widget in a transient popup. Opt-in, off by default.
 */
class ShutterSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val enabled
        get() = dataStore.data.map { it.shuttersEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(shuttersEnabled = enabled) }
    }

    val widgets
        get() = dataStore.data.map { it.shutterWidgets }.distinctUntilChanged()

    fun widgetFor(packageName: String) = dataStore.data
        .map { it.shutterWidgets[packageName] }
        .distinctUntilChanged()

    fun setWidget(packageName: String, ref: ShutterWidgetRef?) {
        dataStore.update {
            it.copy(
                shutterWidgets = if (ref != null) {
                    it.shutterWidgets + (packageName to ref)
                } else {
                    it.shutterWidgets - packageName
                }
            )
        }
    }
}
