package de.mm20.launcher2.ui.settings.floating

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.FloatingLauncherZone
import de.mm20.launcher2.preferences.FloatingLauncherZoneConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.ColorPreference
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.PreferenceWithSwitch
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.floating.FloatingLauncherService
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object FloatingLauncherSettingsRoute : NavKey

@Composable
fun FloatingLauncherSettingsScreen() {
    val viewModel: FloatingLauncherSettingsScreenVM =
        viewModel(factory = FloatingLauncherSettingsScreenVM.Factory)
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsStateWithLifecycle(null)
    val enabled by viewModel.enabled.collectAsStateWithLifecycle(false)
    val zones by viewModel.zones.collectAsStateWithLifecycle(emptyMap())
    val thickness by viewModel.thickness.collectAsStateWithLifecycle(24)
    val color by viewModel.color.collectAsStateWithLifecycle(0xFF6750A4.toInt())
    val alpha by viewModel.alpha.collectAsStateWithLifecycle(0.6f)
    val columns by viewModel.columns.collectAsStateWithLifecycle(2)
    val maxPerColumn by viewModel.maxPerColumn.collectAsStateWithLifecycle(10)
    val hideIndicator by viewModel.hideIndicator.collectAsStateWithLifecycle(false)
    val hapticFeedback by viewModel.hapticFeedback.collectAsStateWithLifecycle(true)
    val autoHideGaming by viewModel.autoHideGaming.collectAsStateWithLifecycle(false)

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
                    PreferenceWithSwitch(
                        title = stringResource(zoneLabelRes(zone)),
                        summary = pluralStringResource(
                            R.plurals.floating_launcher_zone_apps_count,
                            config.apps.size,
                            config.apps.size,
                        ),
                        onClick = { backStack.add(FloatingLauncherZoneAppsRoute(zone.name)) },
                        switchValue = config.enabled,
                        onSwitchChanged = { viewModel.setZoneEnabled(zone, it) },
                    )
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_appearance)) {
                Text(
                    text = stringResource(R.string.preference_floating_launcher_preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                TabPreview(thickness = thickness, color = color, alpha = alpha)
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
                SwitchPreference(
                    title = stringResource(R.string.preference_floating_launcher_columns),
                    summary = stringResource(R.string.preference_floating_launcher_columns_summary),
                    value = columns == 2,
                    onValueChanged = { viewModel.setColumns(if (it) 2 else 1) },
                )
                SliderPreference(
                    title = stringResource(R.string.preference_floating_launcher_max_per_column),
                    value = maxPerColumn,
                    min = 3,
                    max = 20,
                    onValueChanged = { viewModel.setMaxPerColumn(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_floating_launcher_hide_indicator),
                    summary = stringResource(R.string.preference_floating_launcher_hide_indicator_summary),
                    value = hideIndicator,
                    onValueChanged = { viewModel.setHideIndicator(it) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_behavior)) {
                SwitchPreference(
                    title = stringResource(R.string.preference_floating_launcher_haptic_feedback),
                    summary = stringResource(R.string.preference_floating_launcher_haptic_feedback_summary),
                    value = hapticFeedback,
                    onValueChanged = { viewModel.setHapticFeedback(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_floating_launcher_auto_hide_gaming),
                    summary = stringResource(R.string.preference_floating_launcher_auto_hide_gaming_summary),
                    value = autoHideGaming,
                    onValueChanged = { viewModel.setAutoHideGaming(it) },
                )
            }
        }
    }
}

/**
 * Mirrors ZoneTab's own size/shape/color logic so this preview always matches what actually
 * renders in the overlay - a right-edge tab, since that's where the only zone enabled by default
 * (RightTop) lives.
 */
@Composable
private fun TabPreview(thickness: Int, color: Int, alpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .size(width = thickness.dp, height = 72.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .background(Color(color).copy(alpha = alpha)),
        )
    }
}

internal fun zoneLabelRes(zone: FloatingLauncherZone): Int = when (zone) {
    FloatingLauncherZone.LeftTop -> R.string.floating_launcher_zone_left_top
    FloatingLauncherZone.LeftMiddle -> R.string.floating_launcher_zone_left_middle
    FloatingLauncherZone.LeftBottom -> R.string.floating_launcher_zone_left_bottom
    FloatingLauncherZone.RightTop -> R.string.floating_launcher_zone_right_top
    FloatingLauncherZone.RightMiddle -> R.string.floating_launcher_zone_right_middle
    FloatingLauncherZone.RightBottom -> R.string.floating_launcher_zone_right_bottom
}
