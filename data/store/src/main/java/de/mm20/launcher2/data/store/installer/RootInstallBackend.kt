package de.mm20.launcher2.data.store.installer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File

/**
 * Installs an APK by shelling out to `pm install` through a root shell. Same mechanism as
 * `RootProvider` in `:services:freeze`: invoking `su` is itself what triggers the device's
 * superuser-grant prompt (Magisk, KernelSU, ...) on first use, so availability and permission are
 * the same check here.
 */
internal class RootInstallBackend : InstallBackend {

    private var hasRoot: Boolean? = null

    override suspend fun isAvailable(): Boolean {
        hasRoot?.let { return it }
        val granted = runAsRoot("id")
        if (granted) hasRoot = true
        return granted
    }

    override suspend fun hasPermission(): Boolean = isAvailable()

    override suspend fun requestPermission(): Boolean = isAvailable()

    override suspend fun install(apk: File, packageName: String, allowDowngrade: Boolean): BackendInstallResult {
        if (!hasPermission()) {
            return BackendInstallResult.Failed("Root is not available")
        }
        return withContext(Dispatchers.IO) {
            try {
                installPrivilegedSession(
                    apk = apk,
                    userId = android.os.Process.myUid() / 100000,
                    preserveEnabled = android.os.Build.VERSION.SDK_INT >= 34,
                    allowDowngrade = allowDowngrade,
                ) { command, input ->
                    // All arguments are generated internally (no paths or package names).
                    runInstallCommand(input, ProcessBuilder("su", "-c", command.joinToString(" ")).start())
                }
            } catch (error: Exception) {
                BackendInstallResult.Failed("Root install failed", error)
            }
        }
    }

    private suspend fun runAsRoot(command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { stdin ->
                stdin.writeBytes("$command\n")
                stdin.writeBytes("exit\n")
                stdin.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.w(TAG, "su command failed: $command", e)
            false
        }
    }

    companion object {
        private const val TAG = "RootInstallBackend"
    }
}
