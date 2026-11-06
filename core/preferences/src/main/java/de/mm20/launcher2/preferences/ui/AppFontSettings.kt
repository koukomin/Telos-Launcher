package de.mm20.launcher2.preferences.ui

import android.content.Context
import android.content.SharedPreferences

class AppFontSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("telos_app_font_scales", Context.MODE_PRIVATE)

    fun getFontScale(appKey: String): Float {
        return prefs.getFloat("scale_$appKey", 1.0f).coerceIn(0.75f, 1.50f)
    }

    fun setFontScale(appKey: String, scale: Float) {
        prefs.edit().putFloat("scale_$appKey", scale.coerceIn(0.75f, 1.50f)).apply()
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun prefKey(appKey: String): String = "scale_$appKey"
}
