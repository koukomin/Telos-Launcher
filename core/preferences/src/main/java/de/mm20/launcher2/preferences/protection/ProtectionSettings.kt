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
        get() = dataStore.data.map { it.protectionLockSensitiveSettings }
            .distinctUntilChanged()

    fun setLockSensitiveSettings(locked: Boolean) {
        dataStore.update { it.copy(protectionLockSensitiveSettings = locked) }
    }

    val lockMethod
        get() = dataStore.data.map { it.protectionLockMethod }
            .distinctUntilChanged()

    fun setLockMethod(method: SettingsLockMethod) {
        dataStore.update { it.copy(protectionLockMethod = method) }
    }
}
