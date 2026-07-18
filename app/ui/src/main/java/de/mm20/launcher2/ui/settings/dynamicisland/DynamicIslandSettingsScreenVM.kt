package de.mm20.launcher2.ui.settings.dynamicisland

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.ui.DynamicIslandSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class DynamicIslandSettingsScreenVM(
    private val settings: DynamicIslandSettings,
    private val permissionsManager: PermissionsManager,
) : ViewModel() {

    val enabled = settings.enabled
    fun setEnabled(enabled: Boolean) = settings.setEnabled(enabled)

    val showCalls = settings.showCalls
    fun setShowCalls(showCalls: Boolean) = settings.setShowCalls(showCalls)

    val hasOverlayPermission = permissionsManager.hasPermission(PermissionGroup.OverlayWindow)
    fun requestOverlayPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.OverlayWindow)
    }

    val hasPhoneStatePermission = permissionsManager.hasPermission(PermissionGroup.PhoneState)
    fun requestPhoneStatePermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.PhoneState)
    }

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                DynamicIslandSettingsScreenVM(
                    settings = get(),
                    permissionsManager = get(),
                )
            }
        }
    }
}
