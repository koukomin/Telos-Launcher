package de.mm20.launcher2.ui.settings.freeze

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.freeze.FreezeBackendType
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable
import androidx.appcompat.app.AppCompatActivity

@Serializable
data object FreezeSettingsRoute : NavKey

@Composable
fun FreezeSettingsScreen() {
    val context = LocalContext.current
    val viewModel: FreezeSettingsScreenVM = viewModel()

    LaunchedEffect(Unit) {
        viewModel.refreshBackendState()
    }

    val activeBackend by viewModel.activeBackend.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()

    val autoFreezeEnabled by viewModel.autoFreezeEnabled.collectAsStateWithLifecycle()
    val freezeOnScreenOff by viewModel.freezeOnScreenOff.collectAsStateWithLifecycle()
    val freezeOnIdle by viewModel.freezeOnIdle.collectAsStateWithLifecycle()
    val idleTimeoutMinutes by viewModel.idleTimeoutMinutes.collectAsStateWithLifecycle()
    val freezeOnBatterySaver by viewModel.freezeOnBatterySaver.collectAsStateWithLifecycle()

    val apps by viewModel.allApps.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_freeze),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            PreferenceCategory(title = stringResource(R.string.preference_freeze_backend_category)) {
                if (activeBackend == null) {
                    Banner(
                        text = stringResource(R.string.freeze_no_backend_available),
                        icon = R.drawable.error_24px,
                    )
                } else {
                    Preference(
                        icon = R.drawable.ac_unit_24px,
                        title = stringResource(
                            when (activeBackend) {
                                FreezeBackendType.Shizuku -> R.string.freeze_backend_shizuku
                                FreezeBackendType.Root -> R.string.freeze_backend_root
                                null -> R.string.freeze_backend_none
                            }
                        ),
                        summary = stringResource(
                            if (hasPermission == true) R.string.freeze_permission_granted
                            else R.string.freeze_permission_not_granted
                        ),
                        onClick = {
                            (context as? AppCompatActivity)?.let { viewModel.requestPermission(it) }
                        }
                    )
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_freeze_auto_category)) {
                SwitchPreference(
                    title = stringResource(R.string.preference_freeze_auto_enabled),
                    summary = stringResource(R.string.preference_freeze_auto_enabled_summary),
                    value = autoFreezeEnabled == true,
                    onValueChanged = { viewModel.setAutoFreezeEnabled(it) }
                )
                AnimatedVisibility(autoFreezeEnabled == true) {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.preference_freeze_on_screen_off),
                            value = freezeOnScreenOff == true,
                            onValueChanged = { viewModel.setFreezeOnScreenOff(it) }
                        )
                        SwitchPreference(
                            title = stringResource(R.string.preference_freeze_on_idle),
                            value = freezeOnIdle == true,
                            onValueChanged = { viewModel.setFreezeOnIdle(it) }
                        )
                        AnimatedVisibility(freezeOnIdle == true) {
                            SliderPreference(
                                title = stringResource(R.string.preference_freeze_idle_timeout),
                                value = idleTimeoutMinutes ?: 15,
                                min = 1,
                                max = 120,
                                step = 1,
                                onValueChanged = { viewModel.setIdleTimeoutMinutes(it) },
                                label = { Text("$it") }
                            )
                        }
                        SwitchPreference(
                            title = stringResource(R.string.preference_freeze_on_battery_saver),
                            value = freezeOnBatterySaver == true,
                            onValueChanged = { viewModel.setFreezeOnBatterySaver(it) }
                        )
                    }
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_freeze_apps_category)) {
                Text(
                    text = stringResource(R.string.preference_freeze_apps_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(apps, key = { _, it -> it.key }) { _, app ->
            val icon by viewModel.getIcon(app, 32.dp.value.toInt()).collectAsStateWithLifecycle(null)
            val isCandidate = candidates.contains(app.componentName.packageName)
            Preference(
                title = app.label,
                icon = {
                    ShapedLauncherIcon(size = 32.dp, icon = { icon })
                },
                onClick = { viewModel.setCandidateEnabled(app, !isCandidate) },
                controls = {
                    Checkbox(
                        checked = isCandidate,
                        onCheckedChange = { viewModel.setCandidateEnabled(app, it) },
                    )
                }
            )
        }
    }
}
