package de.mm20.launcher2.comms.youtube

import android.content.Context
import de.mm20.launcher2.comms.remote.SecretBox

/** Call credential access on IO: Android Keystore protects stored cookies. */
object YouTubeAccountManager {
    private const val PREFS = "youtube_account"
    private const val KEY = "cookie_encrypted"
    fun setCookie(context: Context, cookie: String) {
        require(cookie.length <= 64_000 && YouTubeSessionPolicy.sapisid(cookie) != null)
        val encrypted = SecretBox.encrypt(cookie)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, encrypted).remove("cookie").putBoolean("expired", false).apply()
        clearPlaylistCache(context)
    }
    fun getCookie(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("expired", false)) return null
        val saved = prefs.getString(KEY, null)
        if (saved != null) return SecretBox.decrypt(saved).takeIf { YouTubeSessionPolicy.sapisid(it) != null }
        val legacy = prefs.getString("cookie", null) ?: return null
        return runCatching { setCookie(context, legacy); legacy }.getOrNull()
    }
    fun markExpired(context: Context, expired: Boolean = true) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("expired", expired)
        if (expired) { edit.remove(KEY).remove("cookie"); clearPlaylistCache(context) }
        edit.apply()
    }
    fun isExpired(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("expired", false)
    fun isValid(context: Context): Boolean = getCookie(context) != null
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        clearPlaylistCache(context)
    }
    private fun clearPlaylistCache(context: Context) {
        context.cacheDir.listFiles()?.filter { it.name.startsWith("user_playlists_cache") }?.forEach { it.delete() }
    }
}
