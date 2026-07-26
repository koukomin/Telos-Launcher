package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A small pill overlay, drawn over other apps via SYSTEM_ALERT_WINDOW like the Floating
 * Launcher, showing whichever of active call / running timer / media playback / charging is
 * currently most relevant. Opt-in, off by default.
 */
class DynamicIslandSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val enabled
        get() = dataStore.data.map { it.dynamicIsland.dynamicIslandEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(dynamicIsland = it.dynamicIsland.copy(dynamicIslandEnabled = enabled)) }
    }

    val showCalls
        get() = dataStore.data.map { it.dynamicIsland.dynamicIslandShowCalls }.distinctUntilChanged()

    fun setShowCalls(showCalls: Boolean) {
        dataStore.update { it.copy(dynamicIsland = it.dynamicIsland.copy(dynamicIslandShowCalls = showCalls)) }
    }
}
