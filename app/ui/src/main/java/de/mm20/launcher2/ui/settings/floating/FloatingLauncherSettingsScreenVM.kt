package de.mm20.launcher2.ui.settings.floating

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.FloatingLauncherZone
import de.mm20.launcher2.preferences.ui.FloatingLauncherSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class FloatingLauncherSettingsScreenVM(
    private val floatingLauncherSettings: FloatingLauncherSettings,
    private val permissionsManager: PermissionsManager,
) : ViewModel() {

    val hasOverlayPermission = permissionsManager.hasPermission(PermissionGroup.OverlayWindow)

    fun requestOverlayPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.OverlayWindow)
    }

    val enabled = floatingLauncherSettings.enabled
    fun setEnabled(enabled: Boolean) = floatingLauncherSettings.setEnabled(enabled)

    val zones = floatingLauncherSettings.zones
    fun setZoneEnabled(zone: FloatingLauncherZone, zoneEnabled: Boolean) =
        floatingLauncherSettings.setZoneEnabled(zone, zoneEnabled)

    val thickness = floatingLauncherSettings.thickness
    fun setThickness(thickness: Int) = floatingLauncherSettings.setThickness(thickness)

    val color = floatingLauncherSettings.color
    fun setColor(color: Int) = floatingLauncherSettings.setColor(color)

    val alpha = floatingLauncherSettings.alpha
    fun setAlpha(alpha: Float) = floatingLauncherSettings.setAlpha(alpha)

    val columns = floatingLauncherSettings.columns
    fun setColumns(columns: Int) = floatingLauncherSettings.setColumns(columns)

    val maxPerColumn = floatingLauncherSettings.maxPerColumn
    fun setMaxPerColumn(maxPerColumn: Int) = floatingLauncherSettings.setMaxPerColumn(maxPerColumn)

    val hideIndicator = floatingLauncherSettings.hideIndicator
    fun setHideIndicator(hide: Boolean) = floatingLauncherSettings.setHideIndicator(hide)

    val hapticFeedback = floatingLauncherSettings.hapticFeedback
    fun setHapticFeedback(enabled: Boolean) = floatingLauncherSettings.setHapticFeedback(enabled)

    val autoHideGaming = floatingLauncherSettings.autoHideGaming
    fun setAutoHideGaming(enabled: Boolean) = floatingLauncherSettings.setAutoHideGaming(enabled)

    val showLabels = floatingLauncherSettings.showLabels
    fun setShowLabels(show: Boolean) = floatingLauncherSettings.setShowLabels(show)

    val panelAlpha = floatingLauncherSettings.panelAlpha
    fun setPanelAlpha(alpha: Float) = floatingLauncherSettings.setPanelAlpha(alpha)

    val iconSize = floatingLauncherSettings.iconSize
    fun setIconSize(size: Int) = floatingLauncherSettings.setIconSize(size)

    val floatingWindows = floatingLauncherSettings.floatingWindows
    fun setFloatingWindows(enabled: Boolean) = floatingLauncherSettings.setFloatingWindows(enabled)

    val tools = floatingLauncherSettings.tools
    fun setTools(enabled: Boolean) = floatingLauncherSettings.setTools(enabled)

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                FloatingLauncherSettingsScreenVM(
                    floatingLauncherSettings = get(),
                    permissionsManager = get(),
                )
            }
        }
    }
}
