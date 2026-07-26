package de.mm20.launcher2.appmanagement

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
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
 * Handles communication with Shizuku to perform privileged operations like force-stopping
 * or disabling apps.
 */
class ShizukuManager(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuManager"
        private const val REQUEST_CODE = 1001
        private const val OWN_PACKAGE = "de.mm20.launcher2"
    }

    private val isRoot: Boolean
        get() = runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)

    private val callerPackage: String
        get() = if (isRoot) OWN_PACKAGE else "com.android.shell"

    private val userId: Int
        get() = Process.myUid() / 100000

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    suspend fun requestPermission(): Boolean {
        if (!isAvailable()) return false
        if (hasPermission()) return true

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

    /**
     * Force stops the given package.
     */
    suspend fun forceStopPackage(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext false
        runCatching {
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity"))
            val stub = Class.forName("android.app.IActivityManager\$Stub")
            val am = if (isAtLeastApiLevel(Build.VERSION_CODES.P)) {
                HiddenApiBypass.invoke(stub, null, "asInterface", binder)
            } else {
                stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            }
            
            if (isAtLeastApiLevel(Build.VERSION_CODES.P)) {
                HiddenApiBypass.invoke(am.javaClass, am, "forceStopPackage", packageName, userId)
            } else {
                am.javaClass.getMethod("forceStopPackage", String::class.java, Int::class.javaPrimitiveType)
                    .invoke(am, packageName, userId)
            }
            true
        }.onFailure { Log.e(TAG, "Failed to force stop $packageName", it) }.getOrDefault(false)
    }

    /**
     * Toggles the app's enabled setting using Shizuku.
     */
    suspend fun setPackageEnabled(packageName: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext false
        runCatching {
            val pm = packageManagerInterface() ?: return@withContext false
            val newState = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                if (isRoot) PackageManager.COMPONENT_ENABLED_STATE_DISABLED 
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            }

            if (isAtLeastApiLevel(Build.VERSION_CODES.P)) {
                HiddenApiBypass.invoke(
                    pm.javaClass, pm, "setApplicationEnabledSetting",
                    packageName, newState, 0, userId, callerPackage,
                )
            } else {
                pm.javaClass.getMethod(
                    "setApplicationEnabledSetting",
                    String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, String::class.java,
                ).invoke(pm, packageName, newState, 0, userId, callerPackage)
            }
            true
        }.onFailure { Log.e(TAG, "Failed to set enabled state for $packageName", it) }.getOrDefault(false)
    }

    private fun packageManagerInterface(): Any? {
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
}
