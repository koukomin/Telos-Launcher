package de.mm20.launcher2.ui.settings.advanced

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.applock.AppLockSettingsRoute
import de.mm20.launcher2.ui.settings.workprofile.WorkProfileSettingsRoute
import de.mm20.launcher2.ui.settings.backup.BackupSettingsRoute
import de.mm20.launcher2.ui.settings.debug.DebugSettingsRoute
import de.mm20.launcher2.ui.settings.freeze.FreezeSettingsRoute
import de.mm20.launcher2.ui.settings.plugins.PluginsSettingsRoute
import de.mm20.launcher2.ui.settings.contextprofiles.ContextProfilesSettingsRoute
import de.mm20.launcher2.ui.settings.performance.PerformanceSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object AdvancedSettingsRoute : NavKey

@Composable
fun AdvancedSettingsScreen() {
    val backStack = LocalBackStack.current
    PreferenceScreen(
        title = stringResource(R.string.preference_screen_advanced),
    ) {
        item {
            PreferenceCategory {
                Preference(
                    icon = R.drawable.speed_24px,
                    title = stringResource(id = R.string.preference_screen_performance),
                    summary = stringResource(id = R.string.preference_screen_performance_summary),
                    onClick = {
                        backStack.add(PerformanceSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.dashboard_2_24px,
                    title = stringResource(id = R.string.preference_screen_context_profiles),
                    summary = stringResource(id = R.string.preference_screen_context_profiles_summary),
                    onClick = {
                        backStack.add(ContextProfilesSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.extension_24px,
                    title = stringResource(id = R.string.preference_screen_plugins),
                    summary = stringResource(id = R.string.preference_screen_plugins_summary),
                    onClick = {
                        backStack.add(PluginsSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.ac_unit_24px,
                    title = stringResource(id = R.string.preference_screen_freeze),
                    summary = stringResource(id = R.string.preference_screen_freeze_summary),
                    onClick = {
                        backStack.add(FreezeSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.lock_24px,
                    title = stringResource(id = R.string.preference_screen_app_lock),
                    summary = stringResource(id = R.string.preference_screen_app_lock_summary),
                    onClick = {
                        backStack.add(AppLockSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.enterprise_24px,
                    title = stringResource(id = R.string.preference_screen_work_profile),
                    summary = stringResource(id = R.string.preference_screen_work_profile_summary),
                    onClick = {
                        backStack.add(WorkProfileSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.settings_backup_restore_24px,
                    title = stringResource(id = R.string.preference_screen_backup),
                    summary = stringResource(id = R.string.preference_screen_backup_summary),
                    onClick = {
                        backStack.add(BackupSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.bug_report_24px,
                    title = stringResource(id = R.string.preference_screen_debug),
                    summary = stringResource(id = R.string.preference_screen_debug_summary),
                    onClick = {
                        backStack.add(DebugSettingsRoute)
                    }
                )
            }
        }
    }
}
