package de.mm20.launcher2.ui.settings.floating

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.FloatingLauncherZone
import de.mm20.launcher2.preferences.FloatingLauncherZoneConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.ColorPreference
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
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
    val zones by viewModel.zones.collectAsStateWithLifecycle(emptyMap())
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
            PreferenceCategory(
                title = stringResource(R.string.preference_category_floating_launcher_zones),
            ) {
                Banner(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = stringResource(R.string.preference_floating_launcher_zones_summary),
                    icon = R.drawable.info_24px,
                )
                for (zone in FloatingLauncherZone.entries) {
                    val config = zones[zone] ?: FloatingLauncherZoneConfig()
                    SwitchPreference(
                        title = stringResource(zoneLabelRes(zone)),
                        value = config.enabled,
                        onValueChanged = { viewModel.setZoneEnabled(zone, it) },
                    )
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_appearance)) {
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

private fun zoneLabelRes(zone: FloatingLauncherZone): Int = when (zone) {
    FloatingLauncherZone.LeftTop -> R.string.floating_launcher_zone_left_top
    FloatingLauncherZone.LeftMiddle -> R.string.floating_launcher_zone_left_middle
    FloatingLauncherZone.LeftBottom -> R.string.floating_launcher_zone_left_bottom
    FloatingLauncherZone.RightTop -> R.string.floating_launcher_zone_right_top
    FloatingLauncherZone.RightMiddle -> R.string.floating_launcher_zone_right_middle
    FloatingLauncherZone.RightBottom -> R.string.floating_launcher_zone_right_bottom
}
