package de.mm20.launcher2.freeze.providers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.getSystemService
import de.mm20.launcher2.freeze.PrivilegedAccessProvider

/**
 * Suspends/hides packages via [DevicePolicyManager], which only works once this app holds
 * *device owner* status - a plain device admin grants none of these APIs. Device owner status
 * can only be established out-of-band, via `adb shell dpm set-device-owner <admin component>` on
 * a device with no other accounts or existing owner (see the guided setup screen); there is no
 * in-app action that can request or grant it, unlike Shizuku/root.
 *
 * Narrower than [ShizukuProvider]/[RootProvider]: [forceStopPackage] and [clearCache] have no
 * device-owner-accessible equivalent (both remain signature|privileged-only APIs even for device
 * owners), so they always report failure here rather than silently no-op.
 */
internal class DeviceOwnerProvider(private val context: Context) : PrivilegedAccessProvider {

    private val dpm: DevicePolicyManager?
        get() = context.getSystemService()

    private val admin: ComponentName
        get() = ComponentName(context, DeviceOwnerAdminReceiver::class.java)

    override suspend fun isAvailable(): Boolean {
        return try {
            dpm?.isDeviceOwnerApp(context.packageName) == true
        } catch (e: Throwable) {
            false
        }
    }

    // Device owner status IS the permission - there's no separate runtime grant once held.
    override suspend fun hasPermission(): Boolean = isAvailable()

    // Nothing to prompt: device owner status can only be granted out-of-band via adb, never from
    // inside the app. Just reports whether it's already held.
    override suspend fun requestPermission(): Boolean = isAvailable()

    override suspend fun setPackagesSuspended(
        packageNames: List<String>,
        suspended: Boolean,
    ): Set<String> {
        if (packageNames.isEmpty()) return emptySet()
        val manager = dpm ?: return emptySet()
        return try {
            val failed = manager.setPackagesSuspended(admin, packageNames.toTypedArray(), suspended)
            packageNames.toSet() - failed.toSet()
        } catch (e: Throwable) {
            Log.e(TAG, "setPackagesSuspended failed", e)
            emptySet()
        }
    }

    // DevicePolicyManager has no per-app "enable" API distinct from setApplicationHidden; hiding
    // an app is the device-owner-accessible equivalent of disabling it here.
    override suspend fun setPackagesEnabled(
        packageNames: List<String>,
        enabled: Boolean,
    ): Set<String> {
        if (packageNames.isEmpty()) return emptySet()
        val manager = dpm ?: return emptySet()
        return packageNames.filterTo(mutableSetOf()) { pkg ->
            try {
                manager.setApplicationHidden(admin, pkg, !enabled)
            } catch (e: Throwable) {
                Log.e(TAG, "setApplicationHidden($pkg) failed", e)
                false
            }
        }
    }

    // No device-owner-accessible equivalent of ActivityManager.forceStopPackage; it remains a
    // FORCE_STOP_PACKAGES signature permission even for device owners.
    override suspend fun forceStopPackage(packageName: String): Boolean = false

    // No device-owner-accessible equivalent of PackageManager.deleteApplicationCacheFiles either.
    override suspend fun clearCache(packageName: String): Boolean = false

    companion object {
        private const val TAG = "DeviceOwnerProvider"
    }
}
