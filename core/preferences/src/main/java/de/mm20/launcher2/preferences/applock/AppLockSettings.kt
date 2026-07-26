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
        get() = dataStore.data.map { it.appLock.appLockEnabled }
            .distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(appLock = it.appLock.copy(appLockEnabled = enabled)) }
    }

    val lockMethod
        get() = dataStore.data.map { it.appLock.appLockMethod }
            .distinctUntilChanged()

    fun setLockMethod(method: SettingsLockMethod) {
        dataStore.update { it.copy(appLock = it.appLock.copy(appLockMethod = method)) }
    }

    val detectionMode
        get() = dataStore.data.map { it.appLock.appLockDetectionMode }
            .distinctUntilChanged()

    fun setDetectionMode(mode: AppLockDetectionMode) {
        dataStore.update { it.copy(appLock = it.appLock.copy(appLockDetectionMode = mode)) }
    }

    val lockedPackages
        get() = dataStore.data.map { it.appLock.appLockLockedPackages }
            .distinctUntilChanged()

    fun setLocked(packageName: String, locked: Boolean) {
        dataStore.update {
            val current = it.appLock.appLockLockedPackages
            it.copy(
                appLock = it.appLock.copy(
                    appLockLockedPackages = if (locked) current + packageName else current - packageName
                )
            )
        }
    }

    /** How long after leaving a locked app it can be returned to without re-authenticating,
     * for apps with no entry in [gracePeriodOverrides]. */
    val defaultGracePeriodMs
        get() = dataStore.data.map { it.appLock.appLockDefaultGracePeriodMs }
            .distinctUntilChanged()

    fun setDefaultGracePeriodMs(ms: Long) {
        dataStore.update { it.copy(appLock = it.appLock.copy(appLockDefaultGracePeriodMs = ms)) }
    }

    val gracePeriodOverrides
        get() = dataStore.data.map { it.appLock.appLockGracePeriodOverrides }
            .distinctUntilChanged()

    /** Pass null to remove the override and fall back to [defaultGracePeriodMs]. */
    fun setGracePeriodOverride(packageName: String, ms: Long?) {
        dataStore.update {
            val current = it.appLock.appLockGracePeriodOverrides
            it.copy(
                appLock = it.appLock.copy(
                    appLockGracePeriodOverrides = if (ms == null) current - packageName
                    else current + (packageName to ms)
                )
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
        get() = dataStore.data.map { it.appLock.appLockLockWorkProfileToggle }
            .distinctUntilChanged()

    fun setLockWorkProfileToggle(locked: Boolean) {
        dataStore.update { it.copy(appLock = it.appLock.copy(appLockLockWorkProfileToggle = locked)) }
    }

    /** Web app shortcut keys gated behind authentication before they open. */
    val lockedWebAppShortcuts
        get() = dataStore.data.map { it.appLock.appLockLockedWebAppShortcuts }
            .distinctUntilChanged()

    fun setWebAppShortcutLocked(key: String, locked: Boolean) {
        dataStore.update {
            val current = it.appLock.appLockLockedWebAppShortcuts
            it.copy(
                appLock = it.appLock.copy(
                    appLockLockedWebAppShortcuts = if (locked) current + key else current - key
                )
            )
        }
    }

    /** Opt-in: silently take a front-camera photo on a failed App Lock authentication attempt. */
    val intruderPhotoEnabled
        get() = dataStore.data.map { it.appLock.appLockIntruderPhotoEnabled }
            .distinctUntilChanged()

    fun setIntruderPhotoEnabled(enabled: Boolean) {
        dataStore.update { it.copy(appLock = it.appLock.copy(appLockIntruderPhotoEnabled = enabled)) }
    }

    val intruderPhotoRetentionDays
        get() = dataStore.data.map { it.appLock.appLockIntruderPhotoRetentionDays }
            .distinctUntilChanged()

    /** Clamped to 1..[INTRUDER_PHOTO_MAX_RETENTION_DAYS] regardless of what's passed in, so the
     * hard cap holds even if a caller (or a future settings-import path) tries to set more. */
    fun setIntruderPhotoRetentionDays(days: Int) {
        val clamped = days.coerceIn(1, INTRUDER_PHOTO_MAX_RETENTION_DAYS)
        dataStore.update { it.copy(appLock = it.appLock.copy(appLockIntruderPhotoRetentionDays = clamped)) }
    }

    /** A SAF tree uri string to store intruder photos in, or null for the default app-private
     * location. */
    val intruderPhotoStorageUri
        get() = dataStore.data.map { it.appLock.appLockIntruderPhotoStorageUri }
            .distinctUntilChanged()

    fun setIntruderPhotoStorageUri(uri: String?) {
        dataStore.update { it.copy(appLock = it.appLock.copy(appLockIntruderPhotoStorageUri = uri)) }
    }

    /** Whether intruder photos should be discoverable by the system gallery - only meaningful
     * together with a non-null [intruderPhotoStorageUri]. */
    val intruderPhotoVisibleInGallery
        get() = dataStore.data.map { it.appLock.appLockIntruderPhotoVisibleInGallery }
            .distinctUntilChanged()

    fun setIntruderPhotoVisibleInGallery(visible: Boolean) {
        dataStore.update { it.copy(appLock = it.appLock.copy(appLockIntruderPhotoVisibleInGallery = visible)) }
    }

    /** Independent of [intruderPhotoEnabled]: post a notification on a failed App Lock attempt. */
    val intruderPhotoNotificationEnabled
        get() = dataStore.data.map { it.appLock.appLockIntruderPhotoNotificationEnabled }
            .distinctUntilChanged()

    fun setIntruderPhotoNotificationEnabled(enabled: Boolean) {
        dataStore.update { it.copy(appLock = it.appLock.copy(appLockIntruderPhotoNotificationEnabled = enabled)) }
    }

    /** Whether apps should only relock when the screen is turned off. */
    val relockOnlyOnScreenOff
        get() = dataStore.data.map { it.appLock.appLockRelockOnlyOnScreenOff }.distinctUntilChanged()

    fun setRelockOnlyOnScreenOff(enabled: Boolean) {
        dataStore.update { it.copy(appLock = it.appLock.copy(appLockRelockOnlyOnScreenOff = enabled)) }
    }
}
