package de.mm20.launcher2.ui.settings.tasks
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.PreferenceWithSwitch
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.calendarsearch.CalendarProviderSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object TasksIntegrationSettingsRoute: NavKey

@Composable
fun TasksIntegrationSettingsScreen() {
    val viewModel: TasksSettingsScreenVM = viewModel()
    val activity = LocalActivity.current
    val backStack = LocalBackStack.current

    val isTasksInstalled by viewModel.isTasksAppInstalled.collectAsStateWithLifecycle(null)
    val hasTasksPermission by viewModel.hasTasksPermission.collectAsStateWithLifecycle(null)
    val isTasksSearchEnabled by viewModel.isTasksSearchEnabled.collectAsStateWithLifecycle(false)

    PreferenceScreen(
        title = stringResource(I18nR.string.preference_tasks_integration)
    ) {
        if (isTasksInstalled == false) {
            item {
                PreferenceCategory {
                    Banner(
                        text = stringResource(
                            I18nR.string.preference_tasks_integration_description,
                            stringResource(BaseR.string.app_name)
                        ),
                        icon = BaseR.drawable.info_24px,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        primaryAction = {
                            Button(onClick = {
                                viewModel.downloadTasksApp(activity as AppCompatActivity)
                            }) {
                                Text(stringResource(I18nR.string.action_install))
                            }
                        }
                    )
                }
            }
        }
        if (isTasksInstalled == true) {
            item {
                PreferenceCategory {
                    if (hasTasksPermission == true) {
                        Banner(
                            text = stringResource(I18nR.string.preference_tasks_integration_ready),
                            icon = BaseR.drawable.check_circle_24px,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.shapes.extraSmall
                                )
                                .padding(16.dp),
                        )
                    }
                    GuardedPreference(
                        locked = hasTasksPermission == false,
                        onUnlock = {
                            viewModel.requestTasksPermission(activity as AppCompatActivity)
                        },
                        description = stringResource(I18nR.string.missing_permission_tasks_integration),
                    ) {
                        PreferenceWithSwitch(
                            icon = BaseR.drawable.task_alt_24px,
                            title = stringResource(I18nR.string.preference_search_tasks),
                            summary = stringResource(I18nR.string.preference_search_tasks_summary),
                            switchValue = isTasksSearchEnabled == true && hasTasksPermission == true,
                            onSwitchChanged = {
                                viewModel.setTasksSearchEnabled(it)
                            },
                            enabled = hasTasksPermission == true,
                            onClick = {
                                backStack.add(CalendarProviderSettingsRoute(providerId = "tasks.org"))
                            }
                        )
                    }
                    Preference(
                        title = stringResource(I18nR.string.preference_launch_tasks_app),
                        icon = BaseR.drawable.open_in_new_24px,
                        onClick = {
                            viewModel.launchTasksApp(activity as AppCompatActivity)
                        }
                    )
                }
            }
        }
    }
}