package de.mm20.launcher2.comms.youtube

import android.content.Context

object YouTubeAccountManager {
    private const val PREFS = "youtube_account"
    private const val KEY_COOKIE = "cookie"

    fun setCookie(context: Context, cookie: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COOKIE, cookie)
            .apply()
    }

    fun getCookie(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COOKIE, null)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
