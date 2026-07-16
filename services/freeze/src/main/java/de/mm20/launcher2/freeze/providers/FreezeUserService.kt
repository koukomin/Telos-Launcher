package de.mm20.launcher2.freeze.providers

import android.util.Log
import de.mm20.launcher2.freeze.IFreezeUserService

/**
 * Runs in a separate process with shell (adb-backed Shizuku) or root privilege, bound via
 * [rikka.shizuku.Shizuku.bindUserService]. Keep this minimal: it only shells out to `pm`, which
 * is exactly what `adb shell pm suspend/unsuspend` does, so behavior is consistent across the
 * Shizuku and root backends.
 */
class FreezeUserService : IFreezeUserService.Stub() {

    override fun destroy() {
        System.exit(0)
    }

    override fun runShellCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e("FreezeUserService", "Failed to run: $command", e)
            false
        }
    }
}
