package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.DesktopModeOrientation
import de.mm20.launcher2.preferences.DesktopWallpaperMode
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DesktopModeSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val enabled
        get() = dataStore.data.map { it.desktopMode.desktopModeEnabled }.distinctUntilChanged()

    /** A user-initiated change - always marks the feature as configured, so auto-enable never
     * fires again (including if this is the user turning it back off). */
    fun setEnabled(enabled: Boolean) {
        dataStore.update {
            it.copy(desktopMode = it.desktopMode.copy(desktopModeEnabled = enabled, desktopModeUserConfigured = true))
        }
    }

    val userConfigured
        get() = dataStore.data.map { it.desktopMode.desktopModeUserConfigured }.distinctUntilChanged()

    /** Auto-enable path only: turns the feature on without marking it as user-configured being
     * a deliberate choice already made - it still counts as "configured" so this only ever fires
     * once. */
    fun autoEnable() {
        dataStore.update {
            it.copy(desktopMode = it.desktopMode.copy(desktopModeEnabled = true, desktopModeUserConfigured = true))
        }
    }

    val orientation
        get() = dataStore.data.map { it.desktopMode.desktopModeOrientation }.distinctUntilChanged()

    fun setOrientation(orientation: DesktopModeOrientation) {
        dataStore.update { it.copy(desktopMode = it.desktopMode.copy(desktopModeOrientation = orientation)) }
    }

    val freeformEnabled
        get() = dataStore.data.map { it.desktopMode.desktopModeFreeformEnabled }.distinctUntilChanged()

    fun setFreeformEnabled(enabled: Boolean) {
        dataStore.update { it.copy(desktopMode = it.desktopMode.copy(desktopModeFreeformEnabled = enabled)) }
    }

    val wallpaperMode
        get() = dataStore.data.map { it.desktopMode.desktopWallpaperMode }.distinctUntilChanged()

    fun setWallpaperMode(mode: DesktopWallpaperMode) {
        dataStore.update { it.copy(desktopMode = it.desktopMode.copy(desktopWallpaperMode = mode)) }
    }

    val wallpaperImageUri
        get() = dataStore.data.map { it.desktopMode.desktopWallpaperImageUri }.distinctUntilChanged()

    fun setWallpaperImageUri(uri: String?) {
        dataStore.update { it.copy(desktopMode = it.desktopMode.copy(desktopWallpaperImageUri = uri)) }
    }

    val gridIconSize
        get() = dataStore.data.map { it.desktopMode.desktopGridIconSize }.distinctUntilChanged()

    fun setGridIconSize(size: Int) {
        dataStore.update { it.copy(desktopMode = it.desktopMode.copy(desktopGridIconSize = size)) }
    }
}
