package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * App Network Socket Kill-Switch via Root / Shizuku.
 * Immediately destroys open TCP/UDP sockets of a specific app UID
 * when locked or hidden by Telos Launcher.
 */
object AppNetworkKillSwitch {
    private const val TAG = "AppNetworkKillSwitch"

    suspend fun killAppSockets(uid: Int): Boolean {
        if (!PrivilegedShell.available() || uid <= 0) return false
        return runCatching {
            PrivilegedShell.run("ss -K dst :* uid eq $uid")
            PrivilegedShell.run("cmd netd socket destroy $uid")
            Log.i(TAG, "Destroyed network sockets for UID $uid")
            true
        }.getOrDefault(false)
    }
}
