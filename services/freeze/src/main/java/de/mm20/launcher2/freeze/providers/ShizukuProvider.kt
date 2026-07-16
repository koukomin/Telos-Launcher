package de.mm20.launcher2.freeze.providers

import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import de.mm20.launcher2.freeze.PrivilegedAccessProvider
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.coroutines.resume

/**
 * Suspends/unsuspends packages by calling `IPackageManager.setPackagesSuspendedAsUser` directly
 * over Shizuku's binder, in this process, via [ShizukuBinderWrapper]. This is the same mechanism
 * Hail (GPL-3.0) uses, and is far more reliable than shelling out to `pm suspend`, which fails
 * silently on many devices/ROMs.
 *
 * Requires `dev.rikka.shizuku:api`/`:provider` and the `rikka.shizuku.ShizukuProvider` manifest
 * entry (declared in this module's manifest). Hidden-API reflection goes through
 * [HiddenApiBypass] because these framework methods are on the greylist since API 28.
 */
internal class ShizukuProvider : PrivilegedAccessProvider {

    companion object {
        private const val REQUEST_CODE = 9316
        private const val TAG = "ShizukuProvider"
        private const val OWN_PACKAGE = "de.mm20.launcher2"
    }

    private val isRoot: Boolean
        get() = runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)

    /**
     * The package the OS records as the "suspending package". For adb-backed Shizuku the binder
     * calls run as the shell uid, so it must be com.android.shell; for root it can be us.
     */
    private val callerPackage: String
        get() = if (isRoot) OWN_PACKAGE else "com.android.shell"

    private val userId: Int
        get() = Process.myUid() / 100000

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

    override suspend fun setPackagesSuspended(
        packageNames: List<String>,
        suspended: Boolean,
    ): Set<String> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptySet()
        val pm = packageManager() ?: return@withContext emptySet()
        // setPackagesSuspendedAsUser returns the packages it could NOT toggle, so anything not in
        // the failure list succeeded. Call per-package to isolate individual failures.
        packageNames.filterTo(mutableSetOf()) { pkg ->
            runCatching { setSuspended(pm, pkg, suspended) }
                .onFailure { Log.e(TAG, "setPackagesSuspended($pkg) failed", it) }
                .getOrDefault(false)
        }
    }

    private fun packageManager(): Any? {
        return runCatching {
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package"))
            val stub = Class.forName("android.content.pm.IPackageManager\$Stub")
            if (isAtLeastApiLevel(Build.VERSION_CODES.P)) {
                HiddenApiBypass.invoke(stub, null, "asInterface", binder)
            } else {
                stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            }
        }.onFailure { Log.e(TAG, "Could not obtain IPackageManager", it) }.getOrNull()
    }

    /** @return true if the package ended up in the requested state. */
    private fun setSuspended(pm: Any, packageName: String, suspended: Boolean): Boolean {
        val pkgs = arrayOf(packageName)
        val failed = when {
            isAtLeastApiLevel(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) -> HiddenApiBypass.invoke(
                pm.javaClass, pm, "setPackagesSuspendedAsUser",
                pkgs, suspended, null, null, null, 0, callerPackage, userId, userId,
            )

            isAtLeastApiLevel(Build.VERSION_CODES.Q) -> HiddenApiBypass.invoke(
                pm.javaClass, pm, "setPackagesSuspendedAsUser",
                pkgs, suspended, null, null, null, callerPackage, userId,
            )

            isAtLeastApiLevel(Build.VERSION_CODES.P) -> HiddenApiBypass.invoke(
                pm.javaClass, pm, "setPackagesSuspendedAsUser",
                pkgs, suspended, null, null, null, callerPackage, userId,
            )

            else -> pm.javaClass.getMethod(
                "setPackagesSuspendedAsUser",
                Array<String>::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            ).invoke(pm, pkgs, suspended, userId)
        }
        return (failed as? Array<*>)?.isEmpty() ?: false
    }
}
