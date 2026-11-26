package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Screen-Off Background Socket Purge via Shizuku / Root.
 * Forcefully closes lingering background TCP/UDP sockets when the screen
 * turns off, saving battery and stopping background data leaks.
 */
object ScreenOffNetworkPurge {
    private const val TAG = "ScreenOffNetworkPurge"

    suspend fun purgeSockets(): Boolean {
        if (!PrivilegedShell.available()) return false
        return runCatching {
            PrivilegedShell.run("ss -K")
            PrivilegedShell.run("cmd netd socket destroy")
            Log.i(TAG, "Screen-off network sockets purged")
            true
        }.getOrDefault(false)
    }
}
