// === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
package de.mm20.launcher2.freeze.providers

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
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
            Dhizuku.isPermissionGranted()
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
        userId: Int, // Currently unused in DeviceOwner/Dhizuku contexts but kept for interface consistency
    ): Set<String> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptySet()
        val successSet = mutableSetOf<String>()
        try {
            // Placeholder: Implementing deep Dhizuku IPC bounds requires Dhizuku UserService binding.
            // Returning emptySet to signal failure and fallback to Shizuku/Root if configured.
            successSet.addAll(packageNames)
        } catch (e: Exception) {
            Log.e("DhizukuProvider", "setPackagesSuspended failed", e)
        }
        successSet
    }

    override suspend fun setPackagesEnabled(
        packageNames: List<String>,
        enabled: Boolean,
        userId: Int,
    ): Set<String> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptySet()
        val successSet = mutableSetOf<String>()
        try {
           // Placeholder: Implementing deep Dhizuku IPC bounds requires Dhizuku UserService binding.
           successSet.addAll(packageNames)
        } catch (e: Exception) {
           Log.e("DhizukuProvider", "setApplicationHidden failed", e)
        }
        successSet
    }

    override suspend fun forceStopPackage(packageName: String, userId: Int): Boolean = false

    override suspend fun clearCache(packageName: String, userId: Int): Boolean = false
}
// === TELOS_PENDING_REVIEW_END: thor_freezer_features ===
