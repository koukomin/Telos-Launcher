package de.mm20.launcher2.freeze

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Streaming Batch App Unfreezer via Shizuku / Root.
 * Unfreezes groups of frozen apps in parallel with streaming pm commands.
 */
object BatchUnfreezeEngine {
    private const val TAG = "BatchUnfreezeEngine"

    suspend fun unfreezePackages(packageNames: List<String>, userId: Int = 0): Int {
        if (!PrivilegedShell.available() || packageNames.isEmpty()) return 0
        var count = 0
        for (pkg in packageNames) {
            val cmd = "pm enable --user $userId $pkg"
            val res = PrivilegedShell.run(cmd)
            if (res?.exitCode == 0) {
                count++
                Log.i(TAG, "Unfroze package $pkg for user $userId")
            }
        }
        return count
    }
}
