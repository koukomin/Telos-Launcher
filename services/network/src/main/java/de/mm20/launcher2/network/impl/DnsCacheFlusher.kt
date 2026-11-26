package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * 1-Click System DNS Cache Flusher via Shizuku / Root.
 * Flushes Android netd resolver cache immediately without rebooting.
 */
object DnsCacheFlusher {
    private const val TAG = "DnsCacheFlusher"

    suspend fun flushDnsCache(): Boolean {
        if (!PrivilegedShell.available()) return false
        return runCatching {
            PrivilegedShell.run("cmd netd resolver flushdefaultif")
            PrivilegedShell.run("ndc resolver flushdefaultif")
            Log.i(TAG, "Flushed system DNS cache")
            true
        }.getOrDefault(false)
    }
}
