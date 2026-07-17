package de.mm20.launcher2.ui.settings.floating

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.FloatingLauncherEdge
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

    val edge = floatingLauncherSettings.edge
    fun setEdge(edge: FloatingLauncherEdge) = floatingLauncherSettings.setEdge(edge)

    val position = floatingLauncherSettings.position
    fun setPosition(position: Float) = floatingLauncherSettings.setPosition(position)

    val thickness = floatingLauncherSettings.thickness
    fun setThickness(thickness: Int) = floatingLauncherSettings.setThickness(thickness)

    val color = floatingLauncherSettings.color
    fun setColor(color: Int) = floatingLauncherSettings.setColor(color)

    val alpha = floatingLauncherSettings.alpha
    fun setAlpha(alpha: Float) = floatingLauncherSettings.setAlpha(alpha)

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
