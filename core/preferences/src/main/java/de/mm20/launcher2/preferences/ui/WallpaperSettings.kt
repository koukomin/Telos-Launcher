package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class WallpaperSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val videoPauseOnBatterySaver
        get() = dataStore.data.map { it.videoWallpaperPauseOnBatterySaver }
            .distinctUntilChanged()

    fun setVideoPauseOnBatterySaver(pause: Boolean) {
        dataStore.update { it.copy(videoWallpaperPauseOnBatterySaver = pause) }
    }

    val videoPauseOnThermalThrottling
        get() = dataStore.data.map { it.videoWallpaperPauseOnThermalThrottling }
            .distinctUntilChanged()

    fun setVideoPauseOnThermalThrottling(pause: Boolean) {
        dataStore.update { it.copy(videoWallpaperPauseOnThermalThrottling = pause) }
    }
}
