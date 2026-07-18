package de.mm20.launcher2.ui.settings.contextprofiles

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.ContextProfile
import de.mm20.launcher2.preferences.ui.ContextProfileSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class ContextProfilesSettingsScreenVM(
    private val settings: ContextProfileSettings,
    private val permissionsManager: PermissionsManager,
    private val searchableRepository: SavableSearchableRepository,
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

    val hasNotificationPolicyPermission = permissionsManager.hasPermission(PermissionGroup.NotificationPolicy)
    fun requestNotificationPolicyPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.NotificationPolicy)
    }

    val hasWriteSettingsPermission = permissionsManager.hasPermission(PermissionGroup.WriteSettings)
    fun requestWriteSettingsPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.WriteSettings)
    }

    fun resolveSearchable(key: String): Flow<SavableSearchable?> {
        return searchableRepository.getByKeys(listOf(key)).map { it.firstOrNull() }
    }

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                ContextProfilesSettingsScreenVM(
                    settings = get(),
                    permissionsManager = get(),
                    searchableRepository = get(),
                )
            }
        }
    }
}
