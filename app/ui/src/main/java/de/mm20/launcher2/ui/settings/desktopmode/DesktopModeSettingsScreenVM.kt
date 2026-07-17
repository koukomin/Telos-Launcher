package de.mm20.launcher2.ui.settings.desktopmode

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.desktopmode.DesktopModeManager
import de.mm20.launcher2.preferences.DesktopModeOrientation
import de.mm20.launcher2.preferences.DesktopWallpaperMode
import de.mm20.launcher2.preferences.ui.DesktopModeSettings
import de.mm20.launcher2.wallpapers.WallpapersService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class DesktopModeSettingsScreenVM(
    private val settings: DesktopModeSettings,
    private val manager: DesktopModeManager,
    private val wallpapersService: WallpapersService,
) : ViewModel() {

    val isSupportedOnThisDevice = manager.isSupportedOnThisDevice
    val externalDisplayConnected = manager.externalDisplay

    val enabled = settings.enabled
    fun setEnabled(enabled: Boolean) = settings.setEnabled(enabled)

    val orientation = settings.orientation
    fun setOrientation(orientation: DesktopModeOrientation) = settings.setOrientation(orientation)

    val wallpaperMode = settings.wallpaperMode
    val wallpaperImageUri = settings.wallpaperImageUri

    fun setWallpaperMode(mode: DesktopWallpaperMode) = settings.setWallpaperMode(mode)

    val gridIconSize = settings.gridIconSize
    fun setGridIconSize(size: Int) = settings.setGridIconSize(size)

    fun setWallpaperImage(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val path = wallpapersService.setDesktopWallpaper(uri)
            if (path != null) {
                settings.setWallpaperImageUri(path)
                settings.setWallpaperMode(DesktopWallpaperMode.StaticImage)
            }
            onResult(path != null)
        }
    }

    val isFreeformPotentiallySupported = manager.isFreeformPotentiallySupported
    val freeformPreferenceEnabled = manager.freeformPreferenceEnabled
    val freeformActiveInSystem = manager.freeformActiveInSystem

    private val _freeformShizukuUnavailable = MutableStateFlow(false)
    val freeformShizukuUnavailable = _freeformShizukuUnavailable.asStateFlow()

    private val _freeformShizukuPermissionDenied = MutableStateFlow(false)
    val freeformShizukuPermissionDenied = _freeformShizukuPermissionDenied.asStateFlow()

    /**
     * Toggling this on/off flips the device-wide `enable_freeform_support` OS setting via
     * Shizuku, per the user's explicit requirement that the toggle must support turning it back
     * OFF from the same place too (no adb needed to undo it).
     */
    fun setFreeformEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _freeformShizukuUnavailable.value = false
            _freeformShizukuPermissionDenied.value = false

            if (enabled && !manager.hasFreeformShizukuPermission()) {
                if (!manager.isFreeformShizukuAvailable()) {
                    _freeformShizukuUnavailable.value = true
                    return@launch
                }
                if (!manager.requestFreeformShizukuPermission()) {
                    _freeformShizukuPermissionDenied.value = true
                    return@launch
                }
            }
            manager.setFreeformEnabled(enabled)
        }
    }

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                DesktopModeSettingsScreenVM(
                    settings = get(),
                    manager = get(),
                    wallpapersService = get(),
                )
            }
        }
    }
}
