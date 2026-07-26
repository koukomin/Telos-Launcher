package de.mm20.launcher2.preferences.protection

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.SettingsLockMethod
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Opt-in lock for sensitive settings screens (hidden items, excluded search folders).
 * Authentication is delegated entirely to the system via BiometricPrompt; nothing
 * credential-like is ever stored by the launcher.
 */
class ProtectionSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val lockSensitiveSettings
        get() = dataStore.data.map { it.protection.protectionLockSensitiveSettings }
            .distinctUntilChanged()

    fun setLockSensitiveSettings(locked: Boolean) {
        dataStore.update { it.copy(protection = it.protection.copy(protectionLockSensitiveSettings = locked)) }
    }

    val lockMethod
        get() = dataStore.data.map { it.protection.protectionLockMethod }
            .distinctUntilChanged()

    fun setLockMethod(method: SettingsLockMethod) {
        dataStore.update { it.copy(protection = it.protection.copy(protectionLockMethod = method)) }
    }

    val useCustomLock
        get() = dataStore.data.map { it.protection.protectionUseCustomLock }
            .distinctUntilChanged()

    fun setUseCustomLock(use: Boolean) {
        dataStore.update { it.copy(protection = it.protection.copy(protectionUseCustomLock = use)) }
    }

    val customLockHashed
        get() = dataStore.data.map { it.protection.protectionCustomLockHashed }
            .distinctUntilChanged()

    fun setCustomLockHashed(hashed: String?) {
        dataStore.update { it.copy(protection = it.protection.copy(protectionCustomLockHashed = hashed)) }
    }

    /**
     * Opt-in lock for the launcher's home screen itself. Always uses the system
     * BiometricPrompt via [lockMethod] - the custom PIN option does not apply here.
     */
    val lockLauncher
        get() = dataStore.data.map { it.protection.protectionLockLauncher }
            .distinctUntilChanged()

    fun setLockLauncher(locked: Boolean) {
        dataStore.update { it.copy(protection = it.protection.copy(protectionLockLauncher = locked)) }
    }
}
