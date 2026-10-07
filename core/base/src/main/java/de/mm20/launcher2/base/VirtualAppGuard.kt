package de.mm20.launcher2.base

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Keeps one misbehaving Telos app from taking the launcher down again and again.
 *
 * A Telos app (Music, Radio, Video, Photos) runs in the launcher's process, so a crash in it ends
 * the launcher too. The guard remembers which of these apps is on screen. When the process dies
 * while one is open - a Java crash seen by the crash handler, or a native crash or a hang seen in
 * the system's exit records on the next start - the crash is counted for that app. After
 * [CRASH_LIMIT] crashes within a day the app is switched off (hidden, like "Remove" in the Store)
 * and the user is told, so the launcher itself stays usable. Installing the app again in the Store
 * starts counting from zero.
 */
object VirtualAppGuard {
    const val CRASH_LIMIT = 2
    private const val PREFS = "virtual_app_guard"
    private const val DAY = 24 * 60 * 60 * 1000L
    private const val HEALTHY_USE = 2 * 60 * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // The apps on screen, oldest first. Opening the photo viewer from the music app leaves both
    // open; a crash is blamed on the one that was opened last.
    private fun stack(p: android.content.SharedPreferences) =
        p.getString("open", "")!!.split('|').filter { it.isNotBlank() }

    /** The app with this key is on screen now */
    @Synchronized
    fun enter(context: Context, key: String) {
        val p = prefs(context)
        val keys = stack(p).filter { it != key } + key
        p.edit().putString("open", keys.joinToString("|")).putLong("openAt", System.currentTimeMillis()).commit()
    }

    /** The app is no longer on screen. Used for a while without a crash, it counts as healthy again. */
    @Synchronized
    fun leave(context: Context, key: String) {
        val p = prefs(context)
        val keys = stack(p)
        if (key !in keys) return
        val usedFor = System.currentTimeMillis() - p.getLong("openAt", 0)
        val rest = keys.filter { it != key }
        val edit = p.edit()
        if (rest.isEmpty()) edit.remove("open").remove("openAt") else edit.putString("open", rest.joinToString("|"))
        if (usedFor >= HEALTHY_USE) edit.remove("crashes:$key").remove("first:$key")
        edit.commit()
    }

    /** Called from the uncaught exception handler: the process is about to die */
    @Synchronized
    fun recordCrashIfOpen(context: Context) {
        val p = prefs(context)
        val key = stack(p).lastOrNull() ?: return
        count(context, key)
        p.edit().remove("open").remove("openAt").commit()
    }

    private fun count(context: Context, key: String) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val first = p.getLong("first:$key", 0)
        val crashes = if (first == 0L || now - first > DAY) 1 else p.getInt("crashes:$key", 0) + 1
        p.edit().putInt("crashes:$key", crashes).putLong("first:$key", if (crashes == 1) now else first).commit()
    }

    /** The user installed the app again: start counting from zero */
    @Synchronized
    fun reset(context: Context, key: String) {
        prefs(context).edit().remove("crashes:$key").remove("first:$key").commit()
    }

    /**
     * Call once when the process starts. Looks for a native crash or hang that the crash handler
     * could not see, and returns the apps that crashed too often and should be switched off now.
     */
    @Synchronized
    fun collectTripped(context: Context, guarded: Collection<String>): List<String> {
        val p = prefs(context)
        val open = stack(p).lastOrNull()
        if (open != null) {
            // the process ended while the app was open: was it a crash or a hang?
            val openedAt = p.getLong("openAt", 0)
            if (Build.VERSION.SDK_INT >= 30 && diedBadlySince(context, openedAt)) count(context, open)
            p.edit().remove("open").remove("openAt").commit()
        }
        val tripped = guarded.filter { p.getInt("crashes:$it", 0) >= CRASH_LIMIT }
        for (key in tripped) reset(context, key)
        if (tripped.isNotEmpty()) Log.w("VirtualAppGuard", "Switching off after repeated crashes: $tripped")
        return tripped
    }

    private fun diedBadlySince(context: Context, since: Long): Boolean = runCatching {
        val am = context.getSystemService(ActivityManager::class.java)
        am.getHistoricalProcessExitReasons(context.packageName, 0, 5).any { info ->
            info.processName == context.packageName && info.timestamp >= since &&
                (info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || info.reason == ApplicationExitInfo.REASON_ANR)
        }
    }.getOrDefault(false)
}
