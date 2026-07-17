package de.mm20.launcher2.ui.settings.contextprofiles

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.ContextProfile
import de.mm20.launcher2.preferences.ui.ContextProfileSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class ContextProfilesSettingsScreenVM(
    private val settings: ContextProfileSettings,
    private val permissionsManager: PermissionsManager,
) : ViewModel() {

    val enabled = settings.enabled
    fun setEnabled(enabled: Boolean) = settings.setEnabled(enabled)

    val profiles = settings.profiles
    fun saveProfile(profile: ContextProfile) = settings.setProfile(profile)
    fun deleteProfile(id: String) = settings.deleteProfile(id)

    val manualOverrideId = settings.manualOverrideId
    fun setManualOverride(id: String?) = settings.setManualOverride(id)

    val hasLocationPermission = permissionsManager.hasPermission(PermissionGroup.Location)
    fun requestLocationPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Location)
    }

    val hasBluetoothPermission = permissionsManager.hasPermission(PermissionGroup.Bluetooth)
    fun requestBluetoothPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Bluetooth)
    }

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                ContextProfilesSettingsScreenVM(
                    settings = get(),
                    permissionsManager = get(),
                )
            }
        }
    }
}
