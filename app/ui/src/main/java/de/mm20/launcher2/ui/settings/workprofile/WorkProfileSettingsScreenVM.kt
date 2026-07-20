package de.mm20.launcher2.ui.settings.workprofile

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.applock.AppLockSettings
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WorkProfileSettingsScreenVM : ViewModel(), KoinComponent {
    private val profileManager: ProfileManager by inject()
    private val appLockSettings: AppLockSettings by inject()
    private val permissionsManager: PermissionsManager by inject()

    val workProfile = profileManager.profiles
        .map { profiles -> profiles.find { it.type == Profile.Type.Work } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val workProfileState = workProfile
        .flatMapLatest { profileManager.getProfileState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setWorkProfilePaused(paused: Boolean) {
        val profile = workProfile.value ?: return
        if (paused) profileManager.lockProfile(profile) else profileManager.unlockProfile(profile)
    }

    val hasManageProfilesPermission = permissionsManager.hasPermission(PermissionGroup.ManageProfiles)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun requestManageProfilesPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.ManageProfiles)
    }

    val lockWorkProfileToggle = appLockSettings.lockWorkProfileToggle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setLockWorkProfileToggle(locked: Boolean) = appLockSettings.setLockWorkProfileToggle(locked)

    val lockMethod = appLockSettings.lockMethod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
}
