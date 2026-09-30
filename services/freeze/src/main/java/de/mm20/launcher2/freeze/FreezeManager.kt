package de.mm20.launcher2.freeze

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import de.mm20.launcher2.freeze.providers.RootProvider
import de.mm20.launcher2.freeze.providers.ShizukuProvider
import de.mm20.launcher2.freeze.providers.IslandProvider
import de.mm20.launcher2.freeze.providers.DeviceOwnerProvider
import de.mm20.launcher2.freeze.providers.DeviceOwnerAdminReceiver
import de.mm20.launcher2.preferences.FreezeBackendPreference
import de.mm20.launcher2.preferences.FreezeMethod
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class FreezeManager internal constructor(
    private val context: Context,
    private val settings: FreezeSettings,
) {
    private val shizukuProvider = ShizukuProvider()
    private val rootProvider = RootProvider()
    private val islandProvider = IslandProvider(context)
    private val deviceOwnerProvider = DeviceOwnerProvider(context)

    private val _activeBackend = MutableStateFlow<FreezeBackendType?>(null)
    val activeBackend: StateFlow<FreezeBackendType?> = _activeBackend.asStateFlow()

    /** Re-evaluates which backend (if any) is currently usable, based on the user's preference. */
    suspend fun refreshBackendState() {
        _activeBackend.value = when (settings.backend.first()) {
            FreezeBackendPreference.ShizukuOnly ->
                FreezeBackendType.Shizuku.takeIf { shizukuProvider.isAvailable() }

            FreezeBackendPreference.RootOnly ->
                FreezeBackendType.Root.takeIf { rootProvider.isAvailable() }

            FreezeBackendPreference.Island ->
                FreezeBackendType.Island.takeIf { islandProvider.isAvailable() }

            FreezeBackendPreference.DeviceOwnerOnly ->
                FreezeBackendType.DeviceOwner.takeIf { deviceOwnerProvider.isAvailable() }

            FreezeBackendPreference.Auto -> when {
                shizukuProvider.isAvailable() -> FreezeBackendType.Shizuku
                rootProvider.isAvailable() -> FreezeBackendType.Root
                deviceOwnerProvider.isAvailable() -> FreezeBackendType.DeviceOwner
                islandProvider.isAvailable() -> FreezeBackendType.Island
                else -> null
            }
        }
    }

    suspend fun hasPermission(): Boolean = when (_activeBackend.value) {
        FreezeBackendType.Shizuku -> shizukuProvider.hasPermission()
        FreezeBackendType.Root -> rootProvider.hasPermission()
        FreezeBackendType.Island -> true // Intent based, no runtime permission for us
        FreezeBackendType.DeviceOwner -> deviceOwnerProvider.hasPermission()
        null -> false
    }

    /** Prompts for permission on the active backend, if any. Suspends until the user responds. */
    suspend fun requestPermission(): Boolean = when (_activeBackend.value) {
        FreezeBackendType.Shizuku -> shizukuProvider.requestPermission()
        FreezeBackendType.Root -> rootProvider.requestPermission()
        FreezeBackendType.Island -> true
        FreezeBackendType.DeviceOwner -> deviceOwnerProvider.requestPermission()
        null -> false
    }

    // === TELOS_PENDING_REVIEW_START: multi_user_freeze ===
    suspend fun freeze(packageName: String, userId: Int = Process.myUid() / 100000): Set<String> {
        if (_activeBackend.value == null) refreshBackendState()
        return setSuspended(listOf(packageName), true, userId)
    }

    suspend fun unfreeze(packageName: String, userId: Int = Process.myUid() / 100000): Set<String> {
        if (_activeBackend.value == null) refreshBackendState()
        return setSuspended(listOf(packageName), false, userId)
    }

    suspend fun freeze(packageNames: List<String>, userId: Int = Process.myUid() / 100000): Set<String> {
        if (_activeBackend.value == null) refreshBackendState()
        return setSuspended(packageNames, true, userId)
    }

    /**
     * Same as [freeze], except when the active backend is [FreezeBackendType.Island]: Island's
     * freeze mechanism is an Activity intent, and Android silently drops Activity launches from
     * a background process (no foreground UI) since API 29 - calling [freeze] directly from
     * AutoFreezeController's screen-off/idle/battery-saver triggers would report success and
     * freeze nothing. This posts a notification instead; the user's tap on it is exempt from that
     * restriction. Shizuku/Root go through the same binder/shell path as [freeze] - no background
     * restriction applies to them, so they freeze immediately as normal.
     */
    suspend fun freezeInBackground(packageNames: List<String>, userId: Int = Process.myUid() / 100000) {
        if (packageNames.isEmpty()) return
        if (_activeBackend.value == FreezeBackendType.Island) {
            islandProvider.postFreezeNotification(packageNames)
        } else {
            freeze(packageNames, userId)
        }
    }

    /** @return the subset of [packageNames] that were actually toggled successfully. */
    private suspend fun setSuspended(packageNames: List<String>, suspended: Boolean, userId: Int = Process.myUid() / 100000): Set<String> {
        if (packageNames.isEmpty()) return emptySet()

        val backend = _activeBackend.value
        if (backend == null) {
            Log.w(TAG, "setSuspended($packageNames, $suspended, user $userId): no active backend")
            return emptySet()
        }
        if (!hasPermission()) {
            Log.w(TAG, "setSuspended($packageNames, $suspended, user $userId): $backend has no permission")
            return emptySet()
        }
        val methods = settings.freezeMethods.first()

        val succeeded = when (backend) {
            FreezeBackendType.Shizuku -> {
                if (suspended) {
                    val toDisable = packageNames.filter { methods[it] == FreezeMethod.Disable }
                    val toSuspend = packageNames.filter { methods[it] != FreezeMethod.Disable }
                    shizukuProvider.setPackagesSuspended(toSuspend, true, userId) +
                            shizukuProvider.setPackagesEnabled(toDisable, false, userId)
                } else {
                    val unsuspended = shizukuProvider.setPackagesSuspended(packageNames, false, userId)
                    val enabled = shizukuProvider.setPackagesEnabled(packageNames, true, userId)
                    unsuspended + enabled
                }
            }

            FreezeBackendType.Root -> {
                if (suspended) {
                    val toDisable = packageNames.filter { methods[it] == FreezeMethod.Disable }
                    val toSuspend = packageNames.filter { methods[it] != FreezeMethod.Disable }
                    rootProvider.setPackagesSuspended(toSuspend, true, userId) +
                            rootProvider.setPackagesEnabled(toDisable, false, userId)
                } else {
                    val unsuspended = rootProvider.setPackagesSuspended(packageNames, false, userId)
                    val enabled = rootProvider.setPackagesEnabled(packageNames, true, userId)
                    unsuspended + enabled
                }
            }

            FreezeBackendType.Island -> {
                if (islandProvider.setPackagesSuspended(packageNames, suspended, userId)) {
                    packageNames.toSet()
                } else {
                    emptySet()
                }
            }

            FreezeBackendType.DeviceOwner -> {
                if (suspended) {
                    val toDisable = packageNames.filter { methods[it] == FreezeMethod.Disable }
                    val toSuspend = packageNames.filter { methods[it] != FreezeMethod.Disable }
                    deviceOwnerProvider.setPackagesSuspended(toSuspend, true, userId) +
                            deviceOwnerProvider.setPackagesEnabled(toDisable, false, userId)
                } else {
                    val unsuspended = deviceOwnerProvider.setPackagesSuspended(packageNames, false, userId)
                    val enabled = deviceOwnerProvider.setPackagesEnabled(packageNames, true, userId)
                    unsuspended + enabled
                }
            }
        }

        val now = System.currentTimeMillis()
        for (packageName in succeeded) {
            if (suspended) settings.recordFrozen(packageName, now) else settings.recordUnfrozen(packageName, now)
        }
        return succeeded
    }

    suspend fun forceStop(packageName: String, userId: Int = Process.myUid() / 100000): Boolean {
        return when (_activeBackend.value) {
            FreezeBackendType.Shizuku -> shizukuProvider.forceStopPackage(packageName, userId)
            FreezeBackendType.Root -> rootProvider.forceStopPackage(packageName, userId)
            else -> false
        }
    }

    suspend fun clearCache(packageName: String, userId: Int = Process.myUid() / 100000): Boolean {
        return when (_activeBackend.value) {
            FreezeBackendType.Shizuku -> shizukuProvider.clearCache(packageName, userId)
            FreezeBackendType.Root -> rootProvider.clearCache(packageName, userId)
            else -> false
        }
    }
    // === TELOS_PENDING_REVIEW_END: multi_user_freeze ===

    /** Live read of the OS-level frozen state (suspended OR disabled). */
    fun isFrozen(packageName: String): Boolean {
        return freezeState(packageName) != AppFreezeState.Normal
    }

    /**
     * Live read of the OS-level state, distinguishing *how* an app is frozen - unlike [isFrozen],
     * which collapses suspended and disabled into one boolean. Reads PackageManager directly each
     * call, not settings.freezeMethods (the user's configured intent), so this reflects reality
     * even if the two have drifted apart (e.g. the method setting was changed after freezing,
     * without re-freezing).
     */
    fun freezeState(packageName: String): AppFreezeState {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(
                packageName,
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
            // Some freeze tools disable only the launcher activity component rather than the
            // whole package, in which case ApplicationInfo.enabled stays true - resolve that
            // component explicitly rather than relying on the package-level flag alone.
            val launcherIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName)
            val launcherActivity = pm.resolveActivity(
                launcherIntent,
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )?.activityInfo
            val isComponentDisabled = launcherActivity != null && (
                !launcherActivity.enabled ||
                    pm.getComponentEnabledSetting(
                        ComponentName(launcherActivity.packageName, launcherActivity.name)
                    ) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                )
            when {
                !info.enabled || isComponentDisabled -> AppFreezeState.Disabled
                (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0 -> AppFreezeState.Suspended
                else -> AppFreezeState.Normal
            }
        } catch (e: PackageManager.NameNotFoundException) {
            // Not visible to a plain lookup - either truly uninstalled, or hidden via
            // setApplicationHidden (Icebox/Island). MATCH_UNINSTALLED_PACKAGES surfaces both;
            // distinguish by checking whether package data still exists on disk.
            try {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
                )
                AppFreezeState.Hidden
            } catch (e: PackageManager.NameNotFoundException) {
                AppFreezeState.Normal
            }
        }
    }

    /** Whether this app currently holds device owner status - only ever true after the user has
     * run the `adb shell dpm set-device-owner` command from the guided setup screen. */
    suspend fun isDeviceOwner(): Boolean = deviceOwnerProvider.isAvailable()

    /** Fully-qualified admin component, for the `adb dpm set-device-owner`/`remove-active-admin`
     * commands shown in the guided setup screen. */
    val deviceOwnerAdminComponent: String
        get() = ComponentName(context, DeviceOwnerAdminReceiver::class.java).flattenToString()

    val autoFreezeEnabled = settings.autoFreezeEnabled
    val autoFreezeCandidates = settings.candidates

    fun setAutoFreezeCandidate(packageName: String, enabled: Boolean) {
        settings.setCandidateEnabled(packageName, enabled)
    }

    /** Freeze/unfreeze counters and last-toggled timestamps, keyed by package name. */
    val stats = settings.stats

    companion object {
        private const val TAG = "FreezeManager"
    }
}
