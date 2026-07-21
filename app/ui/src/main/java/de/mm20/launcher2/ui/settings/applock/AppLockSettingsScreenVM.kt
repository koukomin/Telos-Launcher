package de.mm20.launcher2.ui.settings.applock

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applock.IntruderPhotoManager
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.AppLockDetectionMode
import de.mm20.launcher2.preferences.SettingsLockMethod
import de.mm20.launcher2.preferences.applock.AppLockSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppLockSettingsScreenVM : ViewModel(), KoinComponent {
    private val appLockSettings: AppLockSettings by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val intruderPhotoManager: IntruderPhotoManager by inject()

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

    val intruderPhotoEnabled = appLockSettings.intruderPhotoEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setIntruderPhotoEnabled(enabled: Boolean) =
        appLockSettings.setIntruderPhotoEnabled(enabled)

    val intruderPhotoRetentionDays = appLockSettings.intruderPhotoRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setIntruderPhotoRetentionDays(days: Int) =
        appLockSettings.setIntruderPhotoRetentionDays(days)

    val cameraPermissionGranted = permissionsManager.hasPermission(PermissionGroup.Camera)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun requestCameraPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Camera)
    }

    val intruderPhotoCount = MutableStateFlow(0)

    val intruderPhotoVisibleInGallery = appLockSettings.intruderPhotoVisibleInGallery
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    /** Whether a custom storage folder is set - the gallery-visibility toggle only makes sense
     * once one is, since app-private internal storage (the default) is never gallery-visible
     * regardless of that setting. */
    val hasCustomStorageFolder = appLockSettings.intruderPhotoStorageUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val intruderPhotoStoragePath = MutableStateFlow("")

    init {
        refreshIntruderPhotoCount()
        refreshStoragePath()
    }

    /** The count is a plain file listing, not a Flow, so it can go stale after visiting the
     * gallery screen and deleting photos there - call this whenever the screen re-enters
     * composition to pick that up. */
    fun refreshIntruderPhotoCount() {
        viewModelScope.launch {
            intruderPhotoCount.value = intruderPhotoManager.listPhotos().size
        }
    }

    private fun refreshStoragePath() {
        viewModelScope.launch {
            intruderPhotoStoragePath.value = intruderPhotoManager.currentStorageDisplayPath()
        }
    }

    /** Pass null to reset to the default app-private storage location. */
    fun setCustomStorageFolder(uri: Uri?) {
        viewModelScope.launch {
            intruderPhotoStoragePath.value = intruderPhotoManager.setCustomFolder(uri)
        }
    }

    fun setIntruderPhotoVisibleInGallery(visible: Boolean) {
        viewModelScope.launch {
            intruderPhotoManager.setVisibleInGallery(visible)
        }
    }

    val intruderPhotoNotificationEnabled = appLockSettings.intruderPhotoNotificationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setIntruderPhotoNotificationEnabled(enabled: Boolean) =
        appLockSettings.setIntruderPhotoNotificationEnabled(enabled)
}
