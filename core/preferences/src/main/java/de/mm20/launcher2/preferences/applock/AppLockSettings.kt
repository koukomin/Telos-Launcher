package de.mm20.launcher2.preferences.applock

import de.mm20.launcher2.preferences.AppLockDetectionMode
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.SettingsLockMethod
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Hard ceiling on intruder photo retention, enforced in [AppLockSettings.setIntruderPhotoRetentionDays]
 * itself (not just the settings UI) so no persisted value can ever exceed it. */
const val INTRUDER_PHOTO_MAX_RETENTION_DAYS = 730

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

    /** Require authentication to pause or resume the work profile - a different, independent
     * surface from [enabled]'s per-app locking. */
    val lockWorkProfileToggle
        get() = dataStore.data.map { it.appLockLockWorkProfileToggle }
            .distinctUntilChanged()

    fun setLockWorkProfileToggle(locked: Boolean) {
        dataStore.update { it.copy(appLockLockWorkProfileToggle = locked) }
    }

    /** Web app shortcut keys gated behind authentication before they open. */
    val lockedWebAppShortcuts
        get() = dataStore.data.map { it.appLockLockedWebAppShortcuts }
            .distinctUntilChanged()

    fun setWebAppShortcutLocked(key: String, locked: Boolean) {
        dataStore.update {
            val current = it.appLockLockedWebAppShortcuts
            it.copy(
                appLockLockedWebAppShortcuts = if (locked) current + key else current - key
            )
        }
    }

    /** Opt-in: silently take a front-camera photo on a failed App Lock authentication attempt. */
    val intruderPhotoEnabled
        get() = dataStore.data.map { it.appLockIntruderPhotoEnabled }
            .distinctUntilChanged()

    fun setIntruderPhotoEnabled(enabled: Boolean) {
        dataStore.update { it.copy(appLockIntruderPhotoEnabled = enabled) }
    }

    val intruderPhotoRetentionDays
        get() = dataStore.data.map { it.appLockIntruderPhotoRetentionDays }
            .distinctUntilChanged()

    /** Clamped to 1..[INTRUDER_PHOTO_MAX_RETENTION_DAYS] regardless of what's passed in, so the
     * hard cap holds even if a caller (or a future settings-import path) tries to set more. */
    fun setIntruderPhotoRetentionDays(days: Int) {
        val clamped = days.coerceIn(1, INTRUDER_PHOTO_MAX_RETENTION_DAYS)
        dataStore.update { it.copy(appLockIntruderPhotoRetentionDays = clamped) }
    }

    /** A SAF tree uri string to store intruder photos in, or null for the default app-private
     * location. */
    val intruderPhotoStorageUri
        get() = dataStore.data.map { it.appLockIntruderPhotoStorageUri }
            .distinctUntilChanged()

    fun setIntruderPhotoStorageUri(uri: String?) {
        dataStore.update { it.copy(appLockIntruderPhotoStorageUri = uri) }
    }

    /** Whether intruder photos should be discoverable by the system gallery - only meaningful
     * together with a non-null [intruderPhotoStorageUri]. */
    val intruderPhotoVisibleInGallery
        get() = dataStore.data.map { it.appLockIntruderPhotoVisibleInGallery }
            .distinctUntilChanged()

    fun setIntruderPhotoVisibleInGallery(visible: Boolean) {
        dataStore.update { it.copy(appLockIntruderPhotoVisibleInGallery = visible) }
    }

    /** Independent of [intruderPhotoEnabled]: post a notification on a failed App Lock attempt. */
    val intruderPhotoNotificationEnabled
        get() = dataStore.data.map { it.appLockIntruderPhotoNotificationEnabled }
            .distinctUntilChanged()

    fun setIntruderPhotoNotificationEnabled(enabled: Boolean) {
        dataStore.update { it.copy(appLockIntruderPhotoNotificationEnabled = enabled) }
    }
}
