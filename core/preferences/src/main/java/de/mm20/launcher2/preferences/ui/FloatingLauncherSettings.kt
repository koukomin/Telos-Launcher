package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.FloatingLauncherZone
import de.mm20.launcher2.preferences.FloatingLauncherZoneConfig
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * One UI Edge Panel style overlay: up to six small tabs, one per zone (each screen edge split
 * into thirds), independently toggleable, each expanding into its own quick app launcher, drawn
 * over other apps via SYSTEM_ALERT_WINDOW. Opt-in, off by default; only RightTop is enabled by
 * default among the six zones once turned on.
 */
class FloatingLauncherSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val enabled
        get() = dataStore.data.map { it.floatingLauncherEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(floatingLauncherEnabled = enabled) }
    }

    val zones
        get() = dataStore.data.map { it.floatingLauncherZones }.distinctUntilChanged()

    fun setZoneEnabled(zone: FloatingLauncherZone, enabled: Boolean) {
        dataStore.update {
            val current = it.floatingLauncherZones[zone] ?: FloatingLauncherZoneConfig()
            it.copy(floatingLauncherZones = it.floatingLauncherZones + (zone to current.copy(enabled = enabled)))
        }
    }

    fun setZoneApps(zone: FloatingLauncherZone, apps: List<String>) {
        dataStore.update {
            val current = it.floatingLauncherZones[zone] ?: FloatingLauncherZoneConfig()
            it.copy(floatingLauncherZones = it.floatingLauncherZones + (zone to current.copy(apps = apps)))
        }
    }

    /** Shared across all zones. */
    val columns
        get() = dataStore.data.map { it.floatingLauncherColumns }.distinctUntilChanged()

    fun setColumns(columns: Int) {
        dataStore.update { it.copy(floatingLauncherColumns = columns.coerceIn(1, 2)) }
    }

    /** Caps how many rows the panel shows (per column) before it scrolls. */
    val maxPerColumn
        get() = dataStore.data.map { it.floatingLauncherMaxPerColumn }.distinctUntilChanged()

    fun setMaxPerColumn(maxPerColumn: Int) {
        dataStore.update { it.copy(floatingLauncherMaxPerColumn = maxPerColumn.coerceIn(3, 20)) }
    }

    /** Width in dp of each collapsed tab. */
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

    /** Opacity of each collapsed tab, [0f, 1f]. */
    val alpha
        get() = dataStore.data.map { it.floatingLauncherAlpha }.distinctUntilChanged()

    fun setAlpha(alpha: Float) {
        dataStore.update { it.copy(floatingLauncherAlpha = alpha.coerceIn(0.1f, 1f)) }
    }

    /** When true, tabs are fully invisible (alpha forced to 0 at render time) but still
     * tappable - for anyone who wants the trigger without a visible on-screen element. */
    val hideIndicator
        get() = dataStore.data.map { it.floatingLauncherHideIndicator }.distinctUntilChanged()

    fun setHideIndicator(hide: Boolean) {
        dataStore.update { it.copy(floatingLauncherHideIndicator = hide) }
    }

    val hapticFeedback
        get() = dataStore.data.map { it.floatingLauncherHapticFeedback }.distinctUntilChanged()

    fun setHapticFeedback(enabled: Boolean) {
        dataStore.update { it.copy(floatingLauncherHapticFeedback = enabled) }
    }

    /** Hide every tab while the Gaming context profile is active. */
    val autoHideGaming
        get() = dataStore.data.map { it.floatingLauncherAutoHideGaming }.distinctUntilChanged()

    fun setAutoHideGaming(enabled: Boolean) {
        dataStore.update { it.copy(floatingLauncherAutoHideGaming = enabled) }
    }
}
