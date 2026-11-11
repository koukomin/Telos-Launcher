package de.mm20.launcher2.preferences.theme

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global Cyber-Tech Theme & Performance Controls Manager.
 * Controls master toggles for Holographic Glow, Scanlines, Cyber Grid, Particles, and Parallax.
 */
data class TelosThemeState(
    val enableScanlines: Boolean = true,
    val enableCyberGrid: Boolean = true,
    val enableHoloGlow: Boolean = true,
    val enableWeatherParticles: Boolean = true,
    val enableTiltParallax: Boolean = true
)

class TelosThemeSettings(context: Context) {

    private val prefs = context.getSharedPreferences("telos_cyber_theme", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<TelosThemeState> = _state.asStateFlow()

    private fun loadState(): TelosThemeState {
        return TelosThemeState(
            enableScanlines = prefs.getBoolean("enable_scanlines", true),
            enableCyberGrid = prefs.getBoolean("enable_cyber_grid", true),
            enableHoloGlow = prefs.getBoolean("enable_holo_glow", true),
            enableWeatherParticles = prefs.getBoolean("enable_weather_particles", true),
            enableTiltParallax = prefs.getBoolean("enable_tilt_parallax", true)
        )
    }

    fun setScanlinesEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("enable_scanlines", enabled) }
        _state.value = loadState()
    }

    fun setCyberGridEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("enable_cyber_grid", enabled) }
        _state.value = loadState()
    }

    fun setHoloGlowEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("enable_holo_glow", enabled) }
        _state.value = loadState()
    }

    fun setWeatherParticlesEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("enable_weather_particles", enabled) }
        _state.value = loadState()
    }

    fun setTiltParallaxEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("enable_tilt_parallax", enabled) }
        _state.value = loadState()
    }
}
