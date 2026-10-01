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
        hasRoot = granted
        return granted
    }

    override suspend fun hasPermission(): Boolean = isAvailable()

    override suspend fun requestPermission(): Boolean = isAvailable()

    override suspend fun install(apk: File, packageName: String): BackendInstallResult {
        if (!hasPermission()) {
            return BackendInstallResult.Failed("Root is not available")
        }
        // `-r` (replace existing) and `-d` (allow version downgrade for testing/rollback builds)
        // match what Obtainium and similar sideload tools use for root installs.
        val success = runAsRoot("pm install -r -d \"${apk.absolutePath}\"")
        return if (success) {
            BackendInstallResult.Success
        } else {
            BackendInstallResult.Failed("`pm install` failed via root for $packageName")
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
