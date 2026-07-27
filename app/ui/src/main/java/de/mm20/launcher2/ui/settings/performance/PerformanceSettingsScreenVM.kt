package de.mm20.launcher2.ui.settings.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PerformanceSettingsScreenVM : ViewModel(), KoinComponent {
    private val settings: PerformanceSettings by inject()

    val reduceAnimations = settings.reduceAnimations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val animationSpeed = settings.animationSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val searchDebounceMs = settings.searchDebounceMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val iconCacheSize = settings.iconCacheSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    // Exposed as a 0-100 "bounciness" percentage (0 = no bounce, 100 = max bounce), converted
    // to/from the underlying Spring dampingRatio (1f = no bounce, 0.3f = max bounce we allow).
    val bouncePhysics = settings.bouncePhysics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setReduceAnimations(reduce: Boolean) = settings.setReduceAnimations(reduce)
    fun setAnimationSpeed(speed: Float) = settings.setAnimationSpeed(speed)
    fun setSearchDebounceMs(ms: Int) = settings.setSearchDebounceMs(ms)
    fun setIconCacheSize(size: Int) = settings.setIconCacheSize(size)

    fun setBouncePhysics(dampingRatio: Float) = settings.setBouncePhysics(dampingRatio)
}
