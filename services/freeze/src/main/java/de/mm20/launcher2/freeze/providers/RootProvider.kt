package de.mm20.launcher2.freeze.providers

import de.mm20.launcher2.freeze.PrivilegedAccessProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream

/**
 * Runs `pm suspend`/`pm unsuspend` through a root shell. Requesting permission and checking
 * availability are the same operation here: invoking `su` is what triggers the device's
 * superuser-grant prompt (Magisk, KernelSU, ...) on first use.
 */
internal class RootProvider : PrivilegedAccessProvider {

    private var hasRoot: Boolean? = null

    private val userId: Int
        get() = android.os.Process.myUid() / 100000

    override suspend fun isAvailable(): Boolean {
        hasRoot?.let { return it }
        val granted = runAsRoot("id")
        // Only a positive result is cached: a denied/cancelled su prompt must not lock root out until
        // the process restarts, the user may grant it later.
        if (granted) hasRoot = true
        return granted
    }

    override suspend fun hasPermission(): Boolean = isAvailable()

    override suspend fun requestPermission(): Boolean = isAvailable()

    // === TELOS_PENDING_REVIEW_START: multi_user_freeze ===
    override suspend fun setPackagesSuspended(packageNames: List<String>, suspended: Boolean, userId: Int): Set<String> {
        val action = if (suspended) "suspend" else "unsuspend"
        return packageNames.filterTo(mutableSetOf()) { pkg -> runAsRoot("pm $action --user $userId $pkg") }
    }

    override suspend fun setPackagesEnabled(packageNames: List<String>, enabled: Boolean, userId: Int): Set<String> {
        val action = if (enabled) "enable" else "disable-user"
        return packageNames.filterTo(mutableSetOf()) { pkg -> runAsRoot("pm $action --user $userId $pkg") }
    }

    override suspend fun forceStopPackage(packageName: String, userId: Int): Boolean {
        return runAsRoot("am force-stop --user $userId $packageName")
    }

    override suspend fun clearCache(packageName: String, userId: Int): Boolean {
        // Clearing only cache to avoid data loss
        return runAsRoot("rm -rf /data/user/$userId/$packageName/cache/*")
    }
    // === TELOS_PENDING_REVIEW_END: multi_user_freeze ===

    private suspend fun runAsRoot(command: String): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { stdin ->
                stdin.writeBytes("$command\n")
                stdin.writeBytes("exit\n")
                stdin.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }
}
