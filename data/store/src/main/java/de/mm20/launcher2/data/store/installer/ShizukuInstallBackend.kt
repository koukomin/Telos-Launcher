package de.mm20.launcher2.data.store.installer

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.coroutines.resume

/**
 * Silently installs an APK by shelling `pm install` out through Shizuku's elevated process
 * ([Shizuku.newProcess], the same reflection entry point `ShizukuProvider` in `:services:freeze`
 * already uses, since it isn't part of Shizuku's stable public API).
 *
 * This is the MVP: a shell-level install, not a true `IPackageInstaller` session carried over
 * Shizuku's binder (`ShizukuBinderWrapper` + `IPackageInstaller.Stub.asInterface`, the way
 * `ShizukuProvider.packageManager()` talks to `IPackageManager` directly). That binder-level
 * session is the next step - it avoids spawning a shell process per install and gives proper
 * progress/error callbacks instead of a bare exit code - but isn't implemented yet. [install]
 * documents this as a TODO rather than silently behaving differently from its interface contract.
 */
internal class ShizukuInstallBackend : InstallBackend {

    companion object {
        private const val REQUEST_CODE = 9317
        private const val TAG = "ShizukuInstallBackend"
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun requestPermission(): Boolean {
        if (!isAvailable()) return false
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true

        return suspendCancellableCoroutine { cont ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (cont.isActive) {
                        cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            cont.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    // TODO(store): switch to a binder-level IPackageInstaller session (see class doc) instead of
    //  shelling out to `pm install`, to get real progress/error reporting.
    override suspend fun install(apk: File, packageName: String): BackendInstallResult =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) {
                return@withContext BackendInstallResult.Failed("Shizuku is not authorized")
            }
            try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java,
                )
                method.isAccessible = true
                val command = arrayOf("sh", "-c", "pm install -r \"${apk.absolutePath}\"")
                val process = method.invoke(null, command, null, null)
                val exitCode = process!!::class.java.getMethod("waitFor").invoke(process) as Int
                if (exitCode == 0) {
                    BackendInstallResult.Success
                } else {
                    BackendInstallResult.Failed("`pm install` exited with code $exitCode for $packageName")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku install failed for $packageName", e)
                BackendInstallResult.Failed("Shizuku install failed: ${e.message}", e)
            }
        }
}
