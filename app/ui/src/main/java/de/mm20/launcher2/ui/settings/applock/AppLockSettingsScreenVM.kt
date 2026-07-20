package de.mm20.launcher2.ui.settings.applock

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.AppLockDetectionMode
import de.mm20.launcher2.preferences.SettingsLockMethod
import de.mm20.launcher2.preferences.applock.AppLockSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppLockSettingsScreenVM : ViewModel(), KoinComponent {
    private val appLockSettings: AppLockSettings by inject()
    private val permissionsManager: PermissionsManager by inject()

    val enabled = appLockSettings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setEnabled(enabled: Boolean) = appLockSettings.setEnabled(enabled)

    val lockMethod = appLockSettings.lockMethod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setLockMethod(method: SettingsLockMethod) = appLockSettings.setLockMethod(method)

    val detectionMode = appLockSettings.detectionMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setDetectionMode(mode: AppLockDetectionMode) = appLockSettings.setDetectionMode(mode)

    val lockedPackages = appLockSettings.lockedPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptySet())

    val lockedWebAppShortcuts = appLockSettings.lockedWebAppShortcuts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptySet())

    val defaultGracePeriodMs = appLockSettings.defaultGracePeriodMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setDefaultGracePeriodMs(ms: Long) = appLockSettings.setDefaultGracePeriodMs(ms)

    val lockWorkProfileToggle = appLockSettings.lockWorkProfileToggle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setLockWorkProfileToggle(locked: Boolean) = appLockSettings.setLockWorkProfileToggle(locked)

    val usageAccessGranted = permissionsManager.hasPermission(PermissionGroup.UsageAccess)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun requestUsageAccess(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.UsageAccess)
    }

    val accessibilityGranted = permissionsManager.hasPermission(PermissionGroup.Accessibility)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun requestAccessibility(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Accessibility)
    }

    val hasOverlayPermission = permissionsManager.hasPermission(PermissionGroup.OverlayWindow)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun requestOverlayPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.OverlayWindow)
    }
}
