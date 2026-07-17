package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class PerformanceSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val reduceAnimations
        get() = dataStore.data.map { it.performanceReduceAnimations }.distinctUntilChanged()

    fun setReduceAnimations(reduce: Boolean) {
        dataStore.update { it.copy(performanceReduceAnimations = reduce) }
    }

    val animationSpeed
        get() = dataStore.data.map { it.performanceAnimationSpeed }.distinctUntilChanged()

    fun setAnimationSpeed(speed: Float) {
        dataStore.update { it.copy(performanceAnimationSpeed = speed) }
    }

    val searchDebounceMs
        get() = dataStore.data.map { it.performanceSearchDebounceMs }.distinctUntilChanged()

    fun setSearchDebounceMs(ms: Int) {
        dataStore.update { it.copy(performanceSearchDebounceMs = ms) }
    }

    val iconCacheSize
        get() = dataStore.data.map { it.performanceIconCacheSize }.distinctUntilChanged()

    fun setIconCacheSize(size: Int) {
        dataStore.update { it.copy(performanceIconCacheSize = size) }
    }
}
