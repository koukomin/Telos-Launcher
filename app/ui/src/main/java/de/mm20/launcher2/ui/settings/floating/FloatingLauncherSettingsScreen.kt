package de.mm20.launcher2.ui.settings.floating

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.FloatingLauncherEdge
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ColorPreference
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.floating.FloatingLauncherService
import kotlinx.serialization.Serializable

@Serializable
data object FloatingLauncherSettingsRoute : NavKey

@Composable
fun FloatingLauncherSettingsScreen() {
    val viewModel: FloatingLauncherSettingsScreenVM =
        viewModel(factory = FloatingLauncherSettingsScreenVM.Factory)
    val context = LocalContext.current

    val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsStateWithLifecycle(null)
    val enabled by viewModel.enabled.collectAsStateWithLifecycle(false)
    val edge by viewModel.edge.collectAsStateWithLifecycle(FloatingLauncherEdge.Right)
    val position by viewModel.position.collectAsStateWithLifecycle(0.5f)
    val thickness by viewModel.thickness.collectAsStateWithLifecycle(24)
    val color by viewModel.color.collectAsStateWithLifecycle(0xFF6750A4.toInt())
    val alpha by viewModel.alpha.collectAsStateWithLifecycle(0.6f)

    PreferenceScreen(title = stringResource(R.string.preference_screen_floating_launcher)) {
        item {
            PreferenceCategory {
                GuardedPreference(
                    locked = hasOverlayPermission == false,
                    description = stringResource(R.string.missing_permission_floating_launcher),
                    onUnlock = {
                        viewModel.requestOverlayPermission(context as AppCompatActivity)
                    }
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_floating_launcher),
                        summary = stringResource(R.string.preference_floating_launcher_summary),
                        enabled = hasOverlayPermission != false,
                        value = enabled && hasOverlayPermission == true,
                        onValueChanged = {
                            viewModel.setEnabled(it)
                            val serviceIntent = Intent(context, FloatingLauncherService::class.java)
                            if (it) {
                                ContextCompat.startForegroundService(context, serviceIntent)
                            } else {
                                context.stopService(serviceIntent)
                            }
                        }
                    )
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_appearance)) {
                ListPreference(
                    title = stringResource(R.string.preference_floating_launcher_edge),
                    items = listOf(
                        ListPreferenceItem(
                            stringResource(R.string.preference_floating_launcher_edge_left),
                            FloatingLauncherEdge.Left,
                        ),
                        ListPreferenceItem(
                            stringResource(R.string.preference_floating_launcher_edge_right),
                            FloatingLauncherEdge.Right,
                        ),
                    ),
                    value = edge,
                    onValueChanged = { viewModel.setEdge(it) },
                )
                SliderPreference(
                    title = stringResource(R.string.preference_floating_launcher_position),
                    value = position,
                    min = 0f,
                    max = 1f,
                    onValueChanged = { viewModel.setPosition(it) },
                )
                SliderPreference(
                    title = stringResource(R.string.preference_floating_launcher_thickness),
                    value = thickness,
                    min = 12,
                    max = 48,
                    step = 4,
                    onValueChanged = { viewModel.setThickness(it) },
                )
                ColorPreference(
                    title = stringResource(R.string.preference_floating_launcher_color),
                    value = Color(color),
                    onValueChanged = { viewModel.setColor((it ?: Color(0xFF6750A4)).toArgb()) },
                )
                SliderPreference(
                    title = stringResource(R.string.preference_floating_launcher_alpha),
                    value = alpha,
                    min = 0.1f,
                    max = 1f,
                    onValueChanged = { viewModel.setAlpha(it) },
                )
            }
        }
    }
}
