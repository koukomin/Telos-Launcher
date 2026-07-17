package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.FloatingLauncherEdge
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * One UI Edge Panel style overlay: a small tab pinned to a screen edge that expands into a
 * quick app launcher, drawn over other apps via SYSTEM_ALERT_WINDOW. Opt-in, off by default.
 */
class FloatingLauncherSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val enabled
        get() = dataStore.data.map { it.floatingLauncherEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(floatingLauncherEnabled = enabled) }
    }

    val edge
        get() = dataStore.data.map { it.floatingLauncherEdge }.distinctUntilChanged()

    fun setEdge(edge: FloatingLauncherEdge) {
        dataStore.update { it.copy(floatingLauncherEdge = edge) }
    }

    /** Vertical position of the tab, as a fraction [0f, 1f] of the screen height. */
    val position
        get() = dataStore.data.map { it.floatingLauncherPosition }.distinctUntilChanged()

    fun setPosition(position: Float) {
        dataStore.update { it.copy(floatingLauncherPosition = position.coerceIn(0f, 1f)) }
    }

    /** Width in dp of the collapsed tab. */
    val thickness
        get() = dataStore.data.map { it.floatingLauncherThickness }.distinctUntilChanged()

    fun setThickness(thickness: Int) {
        dataStore.update { it.copy(floatingLauncherThickness = thickness) }
    }

    val color
        get() = dataStore.data.map { it.floatingLauncherColor }.distinctUntilChanged()

    fun setColor(color: Int) {
        dataStore.update { it.copy(floatingLauncherColor = color) }
    }

    /** Opacity of the collapsed tab, [0f, 1f]. */
    val alpha
        get() = dataStore.data.map { it.floatingLauncherAlpha }.distinctUntilChanged()

    fun setAlpha(alpha: Float) {
        dataStore.update { it.copy(floatingLauncherAlpha = alpha.coerceIn(0.1f, 1f)) }
    }
}
