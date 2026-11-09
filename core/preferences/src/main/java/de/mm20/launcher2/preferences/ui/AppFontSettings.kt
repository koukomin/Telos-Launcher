package de.mm20.launcher2.preferences.ui

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppFontSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("telos_app_font_scales", Context.MODE_PRIVATE)

    fun getFontScale(appKey: String): Float {
        return live.getOrPut(appKey) {
            MutableStateFlow(prefs.getFloat("scale_$appKey", 1.0f).coerceIn(MIN, MAX))
        }.value
    }

    fun scaleFlow(appKey: String): StateFlow<Float> =
        live.getOrPut(appKey) {
            MutableStateFlow(prefs.getFloat("scale_$appKey", 1.0f).coerceIn(MIN, MAX))
        }

    /** In-memory only, for slider drag. Does not touch disk. */
    fun setLive(appKey: String, scale: Float) {
        val value = scale.coerceIn(MIN, MAX)
        live.getOrPut(appKey) { MutableStateFlow(value) }.value = value
    }

    fun persist(appKey: String, scale: Float) {
        val value = scale.coerceIn(MIN, MAX)
        setLive(appKey, value)
        prefs.edit().putFloat("scale_$appKey", value).apply()
    }

    fun setFontScale(appKey: String, scale: Float) = persist(appKey, scale)

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun prefKey(appKey: String): String = "scale_$appKey"

    companion object {
        const val MIN = 0.75f
        const val MAX = 1.50f
        private val live = ConcurrentHashMap<String, MutableStateFlow<Float>>()
    }
}
