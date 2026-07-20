package de.mm20.launcher2.preferences.applock

import de.mm20.launcher2.preferences.AppLockDetectionMode
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.SettingsLockMethod
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Per-app biometric lock: gates a chosen set of apps behind the system BiometricPrompt, the same
 * way [de.mm20.launcher2.preferences.protection.ProtectionSettings] gates settings screens -
 * authentication is delegated entirely to the system, nothing credential-like is ever stored
 * here beyond which packages are locked.
 */
class AppLockSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val enabled
        get() = dataStore.data.map { it.appLockEnabled }
            .distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(appLockEnabled = enabled) }
    }

    val lockMethod
        get() = dataStore.data.map { it.appLockMethod }
            .distinctUntilChanged()

    fun setLockMethod(method: SettingsLockMethod) {
        dataStore.update { it.copy(appLockMethod = method) }
    }

    val detectionMode
        get() = dataStore.data.map { it.appLockDetectionMode }
            .distinctUntilChanged()

    fun setDetectionMode(mode: AppLockDetectionMode) {
        dataStore.update { it.copy(appLockDetectionMode = mode) }
    }

    val lockedPackages
        get() = dataStore.data.map { it.appLockLockedPackages }
            .distinctUntilChanged()

    fun setLocked(packageName: String, locked: Boolean) {
        dataStore.update {
            val current = it.appLockLockedPackages
            it.copy(
                appLockLockedPackages = if (locked) current + packageName else current - packageName
            )
        }
    }

    /** How long after leaving a locked app it can be returned to without re-authenticating,
     * for apps with no entry in [gracePeriodOverrides]. */
    val defaultGracePeriodMs
        get() = dataStore.data.map { it.appLockDefaultGracePeriodMs }
            .distinctUntilChanged()

    fun setDefaultGracePeriodMs(ms: Long) {
        dataStore.update { it.copy(appLockDefaultGracePeriodMs = ms) }
    }

    val gracePeriodOverrides
        get() = dataStore.data.map { it.appLockGracePeriodOverrides }
            .distinctUntilChanged()

    /** Pass null to remove the override and fall back to [defaultGracePeriodMs]. */
    fun setGracePeriodOverride(packageName: String, ms: Long?) {
        dataStore.update {
            val current = it.appLockGracePeriodOverrides
            it.copy(
                appLockGracePeriodOverrides = if (ms == null) current - packageName
                else current + (packageName to ms)
            )
        }
    }

    /** One-shot resolution of [packageName]'s effective grace period - its override if it has
     * one, [defaultGracePeriodMs] otherwise. */
    suspend fun gracePeriodMsFor(packageName: String): Long {
        val override = gracePeriodOverrides.first()[packageName]
        if (override != null) return override
        return defaultGracePeriodMs.first()
    }
}
