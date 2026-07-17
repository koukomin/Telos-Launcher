package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.DesktopModeOrientation
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DesktopModeSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val enabled
        get() = dataStore.data.map { it.desktopModeEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(desktopModeEnabled = enabled) }
    }

    val orientation
        get() = dataStore.data.map { it.desktopModeOrientation }.distinctUntilChanged()

    fun setOrientation(orientation: DesktopModeOrientation) {
        dataStore.update { it.copy(desktopModeOrientation = orientation) }
    }

    val freeformEnabled
        get() = dataStore.data.map { it.desktopModeFreeformEnabled }.distinctUntilChanged()

    fun setFreeformEnabled(enabled: Boolean) {
        dataStore.update { it.copy(desktopModeFreeformEnabled = enabled) }
    }
}
