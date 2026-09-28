package de.mm20.launcher2.ui.settings.workprofile

import android.content.Intent
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.SettingsLockMethod
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.settings.protection.authenticateSettings
import de.mm20.launcher2.ui.settings.protection.canAuthenticateSettings
import kotlinx.serialization.Serializable

@Serializable
data object WorkProfileSettingsRoute : NavKey

/**
 * Telos isn't a device policy controller, so it can't create or remove a work profile
 * itself - the setup/remove actions just deep-link into the system's own flow for that. What we
 * do own here is exposing the pause/resume state and its optional lock, previously buried inside
 * App Lock settings.
 */
@Composable
fun WorkProfileSettingsScreen() {
    val context = LocalContext.current
    val viewModel: WorkProfileSettingsScreenVM = viewModel()

    val workProfile by viewModel.workProfile.collectAsStateWithLifecycle()
    val workProfileState by viewModel.workProfileState.collectAsStateWithLifecycle()
    val hasManageProfilesPermission by viewModel.hasManageProfilesPermission.collectAsStateWithLifecycle()
    val lockWorkProfileToggle by viewModel.lockWorkProfileToggle.collectAsStateWithLifecycle()
    val lockMethod by viewModel.lockMethod.collectAsStateWithLifecycle()

    val promptTitle = stringResource(R.string.app_lock_work_profile_prompt_title)

    fun setPaused(paused: Boolean) {
        val activity = context as? FragmentActivity ?: return
        val method = lockMethod ?: SettingsLockMethod.DeviceCredential
        if (lockWorkProfileToggle == true && canAuthenticateSettings(activity, method)) {
            authenticateSettings(activity, method, promptTitle) { success ->
                if (success) viewModel.setWorkProfilePaused(paused)
            }
        } else if (lockWorkProfileToggle != true) {
            viewModel.setWorkProfilePaused(paused)
        }
        // else: locking is on but there's no usable authenticator - fail closed, do nothing.
    }

    fun openSystemUserSettings() {
        context.tryStartActivity(Intent(Settings.ACTION_SETTINGS))
    }

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_work_profile),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            PreferenceCategory {
                Preference(
                    icon = R.drawable.enterprise_24px,
                    title = stringResource(R.string.work_profile_status_title),
                    summary = stringResource(
                        when {
                            workProfile == null -> R.string.work_profile_status_not_set_up
                            workProfileState?.locked == true -> R.string.work_profile_status_paused
                            else -> R.string.work_profile_status_active
                        }
                    ),
                )
                Preference(
                    title = stringResource(
                        if (workProfile == null) R.string.work_profile_setup_button
                        else R.string.work_profile_manage_button
                    ),
                    summary = stringResource(
                        if (workProfile == null) R.string.work_profile_setup_description
                        else R.string.work_profile_manage_description
                    ),
                    onClick = { openSystemUserSettings() },
                )
            }
        }
        if (workProfile != null) {
            item {
                PreferenceCategory {
                    GuardedPreference(
                        locked = hasManageProfilesPermission == false,
                        description = stringResource(R.string.missing_permission_manage_profiles),
                        onUnlock = {
                            (context as? AppCompatActivity)?.let {
                                viewModel.requestManageProfilesPermission(it)
                            }
                        },
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.preference_work_profile_pause),
                            summary = stringResource(R.string.preference_work_profile_pause_summary),
                            enabled = hasManageProfilesPermission == true,
                            value = workProfileState?.locked == true,
                            onValueChanged = { setPaused(it) },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.preference_app_lock_work_profile_toggle),
                            summary = stringResource(R.string.preference_app_lock_work_profile_toggle_summary),
                            enabled = hasManageProfilesPermission == true,
                            value = lockWorkProfileToggle == true,
                            onValueChanged = { viewModel.setLockWorkProfileToggle(it) },
                        )
                    }
                }
            }
        }
    }
}
