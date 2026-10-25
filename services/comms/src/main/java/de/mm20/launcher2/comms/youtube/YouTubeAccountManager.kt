package de.mm20.launcher2.comms.youtube

import android.content.Context

object YouTubeAccountManager {
    private const val PREFS = "youtube_account"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_EXPIRED = "expired"

    fun setCookie(context: Context, cookie: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COOKIE, cookie)
            .putBoolean(KEY_EXPIRED, false)
            .apply()
    }

    fun getCookie(context: Context): String? {
        if (isExpired(context)) return null
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COOKIE, null)
    }

    fun markExpired(context: Context, expired: Boolean = true) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXPIRED, expired)
            .apply()
    }

    fun isExpired(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_EXPIRED, false)
    }

    fun isValid(context: Context): Boolean {
        val cookie = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COOKIE, null)
        return !cookie.isNullOrBlank() && cookie.contains("SAPISID") && !isExpired(context)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
