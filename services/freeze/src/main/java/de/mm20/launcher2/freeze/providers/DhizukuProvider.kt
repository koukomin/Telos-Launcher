// === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
package de.mm20.launcher2.freeze.providers

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.util.Log
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import de.mm20.launcher2.freeze.PrivilegedAccessProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal class DhizukuProvider(private val context: Context) : PrivilegedAccessProvider {

    override suspend fun isAvailable(): Boolean {
        return try {
            Dhizuku.init(context)
            Dhizuku.getVersionCode() > 0
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            Dhizuku.isPermissionGranted() && getDhizukuPolicyManager() != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun requestPermission(): Boolean {
        if (!isAvailable()) return false
        if (hasPermission()) return true

        return suspendCancellableCoroutine { cont ->
            try {
                Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                    override fun onRequestPermission(granted: Int) {
                        if (cont.isActive) cont.resume(granted == PackageManager.PERMISSION_GRANTED)
                    }
                })
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    override suspend fun setPackagesSuspended(
        packageNames: List<String>,
        suspended: Boolean,
        userId: Int, // Dhizuku acts on the current user only
    ): Set<String> = withContext(Dispatchers.IO) {
        if (packageNames.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return@withContext emptySet()
        if (!hasPermission()) return@withContext emptySet()
        try {
            val dpm = getDhizukuPolicyManager() ?: return@withContext emptySet()
            val admin = Dhizuku.getOwnerComponent()
            // Returns the packages that could NOT be changed.
            val failed = dpm.setPackagesSuspended(admin, packageNames.toTypedArray(), suspended)
                ?.toSet() ?: return@withContext emptySet()
            packageNames.filter { it !in failed }.toSet()
        } catch (e: SecurityException) {
            Log.e(TAG, "setPackagesSuspended denied", e)
            emptySet()
        } catch (e: Throwable) {
            Log.e(TAG, "setPackagesSuspended failed", e)
            emptySet()
        }
    }

    override suspend fun setPackagesEnabled(
        packageNames: List<String>,
        enabled: Boolean,
        userId: Int,
    ): Set<String> = withContext(Dispatchers.IO) {
        if (packageNames.isEmpty() || !hasPermission()) return@withContext emptySet()
        val successSet = mutableSetOf<String>()
        try {
            val dpm = getDhizukuPolicyManager() ?: return@withContext emptySet()
            val admin = Dhizuku.getOwnerComponent()
            for (pkg in packageNames) {
                try {
                    // "Disabled" means hidden for a device owner. true is returned only if the state changed.
                    if (dpm.setApplicationHidden(admin, pkg, !enabled)) successSet.add(pkg)
                } catch (e: SecurityException) {
                    Log.e(TAG, "setApplicationHidden denied for $pkg", e)
                } catch (e: Exception) {
                    Log.e(TAG, "setApplicationHidden failed for $pkg", e)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "setPackagesEnabled failed", e)
        }
        successSet
    }

    /**
     * A DevicePolicyManager whose binder goes through Dhizuku, so calls run with the Dhizuku device owner's
     * identity. Uses a fresh context so the shared DevicePolicyManager instance is not touched. Needs reflection
     * on DevicePolicyManager.mService and IDevicePolicyManager.Stub (hidden API), returns null if that fails.
     */
    private fun getDhizukuPolicyManager(): DevicePolicyManager? {
        return try {
            val ctx = context.createPackageContext(context.packageName, 0)
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return null
            val field = DevicePolicyManager::class.java.getDeclaredField("mService")
            field.isAccessible = true
            val original = field.get(dpm) as? IInterface ?: return null
            val wrapped = Dhizuku.binderWrapper(original.asBinder())
            val stub = Class.forName("android.app.admin.IDevicePolicyManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, wrapped)
            field.set(dpm, stub)
            dpm
        } catch (e: Throwable) {
            Log.e(TAG, "Could not create Dhizuku DevicePolicyManager", e)
            null
        }
    }

    private companion object {
        const val TAG = "DhizukuProvider"
    }

    override suspend fun forceStopPackage(packageName: String, userId: Int): Boolean = false

    override suspend fun clearCache(packageName: String, userId: Int): Boolean = false
}
// === TELOS_PENDING_REVIEW_END: thor_freezer_features ===
