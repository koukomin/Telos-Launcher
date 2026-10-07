package de.mm20.launcher2.base

import android.content.Context
import java.io.File

/**
 * Telos can run the video player in its own process (":player"), so that a crash of a decoder
 * cannot take the launcher down. That process starts none of the launcher: no Koin, no settings
 * store (a DataStore must not be opened by two processes), so code that runs there has to
 * check [isolatedPlayer] and use the plain-file or broadcast alternatives.
 */
object ProcessInfo {
    @Volatile var isolatedPlayer = false
        private set

    @Volatile var appContext: Context? = null
        private set

    /** Called first thing in Application.onCreate. Returns true in the player process. */
    fun init(context: Context): Boolean {
        appContext = context.applicationContext ?: context
        isolatedPlayer = runCatching {
            File("/proc/self/cmdline").readText().trim('\u0000').endsWith(":player")
        }.getOrDefault(false)
        return isolatedPlayer
    }

    const val PLAYER_PROCESS = ":player"
}
