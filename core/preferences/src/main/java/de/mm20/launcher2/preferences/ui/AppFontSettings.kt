package de.mm20.launcher2.preferences.ui

import android.content.Context

class AppFontSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("telos_app_font_scales", Context.MODE_PRIVATE)

    fun getFontScale(appKey: String): Float {
        return prefs.getFloat("scale_$appKey", 1.0f)
    }

    fun setFontScale(appKey: String, scale: Float) {
        prefs.edit().putFloat("scale_$appKey", scale).apply()
    }
}
