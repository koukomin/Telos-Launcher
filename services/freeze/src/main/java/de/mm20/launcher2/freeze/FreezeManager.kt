package de.mm20.launcher2.freeze

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import de.mm20.launcher2.freeze.providers.RootProvider
import de.mm20.launcher2.freeze.providers.ShizukuProvider
import de.mm20.launcher2.freeze.providers.IslandProvider
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

            FreezeBackendPreference.Auto -> when {
                shizukuProvider.isAvailable() -> FreezeBackendType.Shizuku
                rootProvider.isAvailable() -> FreezeBackendType.Root
                islandProvider.isAvailable() -> FreezeBackendType.Island
                else -> null
            }
        }
    }

    suspend fun hasPermission(): Boolean = when (_activeBackend.value) {
        FreezeBackendType.Shizuku -> shizukuProvider.hasPermission()
        FreezeBackendType.Root -> rootProvider.hasPermission()
        FreezeBackendType.Island -> true // Intent based, no runtime permission for us
        null -> false
    }

    /** Prompts for permission on the active backend, if any. Suspends until the user responds. */
    suspend fun requestPermission(): Boolean = when (_activeBackend.value) {
        FreezeBackendType.Shizuku -> shizukuProvider.requestPermission()
        FreezeBackendType.Root -> rootProvider.requestPermission()
        FreezeBackendType.Island -> true
        null -> false
    }

    suspend fun freeze(packageName: String): Set<String> {
        if (_activeBackend.value == null) refreshBackendState()
        return setSuspended(listOf(packageName), true)
    }

    suspend fun unfreeze(packageName: String): Set<String> {
        if (_activeBackend.value == null) refreshBackendState()
        return setSuspended(listOf(packageName), false)
    }

    suspend fun freeze(packageNames: List<String>): Set<String> {
        if (_activeBackend.value == null) refreshBackendState()
        return setSuspended(packageNames, true)
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
    suspend fun freezeInBackground(packageNames: List<String>) {
        if (packageNames.isEmpty()) return
        if (_activeBackend.value == FreezeBackendType.Island) {
            islandProvider.postFreezeNotification(packageNames)
        } else {
            freeze(packageNames)
        }
    }

    /** @return the subset of [packageNames] that were actually toggled successfully. */
    private suspend fun setSuspended(packageNames: List<String>, suspended: Boolean): Set<String> {
        if (packageNames.isEmpty()) return emptySet()

        val backend = _activeBackend.value
        if (backend == null) {
            Log.w(TAG, "setSuspended($packageNames, $suspended): no active backend")
            return emptySet()
        }
        if (!hasPermission()) {
            Log.w(TAG, "setSuspended($packageNames, $suspended): $backend has no permission")
            return emptySet()
        }
        val methods = settings.freezeMethods.first()

        val succeeded = when (backend) {
            FreezeBackendType.Shizuku -> {
                if (suspended) {
                    val toDisable = packageNames.filter { methods[it] == FreezeMethod.Disable }
                    val toSuspend = packageNames.filter { methods[it] != FreezeMethod.Disable }
                    shizukuProvider.setPackagesSuspended(toSuspend, true) +
                            shizukuProvider.setPackagesEnabled(toDisable, false)
                } else {
                    val unsuspended = shizukuProvider.setPackagesSuspended(packageNames, false)
                    val enabled = shizukuProvider.setPackagesEnabled(packageNames, true)
                    unsuspended + enabled
                }
            }

            FreezeBackendType.Root -> {
                if (suspended) {
                    val toDisable = packageNames.filter { methods[it] == FreezeMethod.Disable }
                    val toSuspend = packageNames.filter { methods[it] != FreezeMethod.Disable }
                    rootProvider.setPackagesSuspended(toSuspend, true) +
                            rootProvider.setPackagesEnabled(toDisable, false)
                } else {
                    val unsuspended = rootProvider.setPackagesSuspended(packageNames, false)
                    val enabled = rootProvider.setPackagesEnabled(packageNames, true)
                    unsuspended + enabled
                }
            }

            FreezeBackendType.Island -> {
                if (islandProvider.setPackagesSuspended(packageNames, suspended)) {
                    packageNames.toSet()
                } else {
                    emptySet()
                }
            }
        }

        val now = System.currentTimeMillis()
        for (packageName in succeeded) {
            if (suspended) settings.recordFrozen(packageName, now) else settings.recordUnfrozen(packageName, now)
        }
        return succeeded
    }

    suspend fun forceStop(packageName: String): Boolean {
        return when (_activeBackend.value) {
            FreezeBackendType.Shizuku -> shizukuProvider.forceStopPackage(packageName)
            FreezeBackendType.Root -> rootProvider.forceStopPackage(packageName)
            else -> false
        }
    }

    suspend fun clearCache(packageName: String): Boolean {
        return when (_activeBackend.value) {
            FreezeBackendType.Shizuku -> shizukuProvider.clearCache(packageName)
            FreezeBackendType.Root -> rootProvider.clearCache(packageName)
            else -> false
        }
    }

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
            val info = context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
            when {
                !info.enabled -> AppFreezeState.Disabled
                (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0 -> AppFreezeState.Suspended
                else -> AppFreezeState.Normal
            }
        } catch (e: PackageManager.NameNotFoundException) {
            AppFreezeState.Normal
        }
    }

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
