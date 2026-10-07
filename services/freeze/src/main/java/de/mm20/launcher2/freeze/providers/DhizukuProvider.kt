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
        // The freeze calls below are not implemented yet (they would need a Dhizuku user service). Reporting
        // the backend as available would make "System default" pick it over a backend that works and claim
        // success without freezing anything, so it stays unavailable until it can really freeze.
        if (!IMPLEMENTED) return false
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
            // Nothing is changed, so nothing is reported as done.
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
        } catch (e: Exception) {
           Log.e("DhizukuProvider", "setApplicationHidden failed", e)
        }
        successSet
    }

    private companion object {
        const val IMPLEMENTED = false
    }

    override suspend fun forceStopPackage(packageName: String, userId: Int): Boolean = false

    override suspend fun clearCache(packageName: String, userId: Int): Boolean = false
}
// === TELOS_PENDING_REVIEW_END: thor_freezer_features ===
