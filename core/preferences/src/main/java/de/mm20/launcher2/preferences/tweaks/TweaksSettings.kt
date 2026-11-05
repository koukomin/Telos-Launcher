package de.mm20.launcher2.preferences.tweaks

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.TweaksGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TweaksSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val data: Flow<TweaksGroup> get() = dataStore.data.map { it.tweaks }

    fun setPersistOnBoot(enabled: Boolean) {
        dataStore.update { it.copy(tweaks = it.tweaks.copy(persistOnBoot = enabled)) }
    }

    fun setClockSeconds(enabled: Boolean) {
        dataStore.update { it.copy(tweaks = it.tweaks.copy(clockSeconds = enabled)) }
    }

    fun setPeakRefresh120(enabled: Boolean) {
        dataStore.update { it.copy(tweaks = it.tweaks.copy(peakRefresh120 = enabled)) }
    }

    fun setDisableSafeVolume(enabled: Boolean) {
        dataStore.update { it.copy(tweaks = it.tweaks.copy(disableSafeVolume = enabled)) }
    }

    fun setCallVibrateConnect(enabled: Boolean) {
        dataStore.update { it.copy(tweaks = it.tweaks.copy(callVibrateConnect = enabled)) }
    }

    fun setMobileDataAlwaysOn(enabled: Boolean) {
        dataStore.update { it.copy(tweaks = it.tweaks.copy(mobileDataAlwaysOn = enabled)) }
    }

    fun setAlwaysOnDisplay(enabled: Boolean) {
        dataStore.update { it.copy(tweaks = it.tweaks.copy(alwaysOnDisplay = enabled)) }
    }
}
