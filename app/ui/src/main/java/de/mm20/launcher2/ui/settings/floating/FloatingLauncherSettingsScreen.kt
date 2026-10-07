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
import androidx.compose.ui.BiasAlignment
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
import de.mm20.launcher2.preferences.FloatingLauncherEdge
import de.mm20.launcher2.preferences.FloatingLauncherZone
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.ColorPreference
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.PreferenceWithSwitch
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.floating.FloatingLauncherService
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data object FloatingLauncherSettingsRoute : NavKey

@Composable
fun FloatingLauncherSettingsScreen() {
    val viewModel: FloatingLauncherSettingsScreenVM =
        viewModel(factory = FloatingLauncherSettingsScreenVM.Factory)
    val context = LocalContext.current

    val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsStateWithLifecycle(null)
    val enabled by viewModel.enabled.collectAsStateWithLifecycle(false)
    val thickness by viewModel.thickness.collectAsStateWithLifecycle(24)
    val color by viewModel.color.collectAsStateWithLifecycle(0xFF9E9E9E.toInt())
    val alpha by viewModel.alpha.collectAsStateWithLifecycle(0.8f)
    val columns by viewModel.columns.collectAsStateWithLifecycle(2)
    val maxPerColumn by viewModel.maxPerColumn.collectAsStateWithLifecycle(10)
    val hideIndicator by viewModel.hideIndicator.collectAsStateWithLifecycle(false)
    val hapticFeedback by viewModel.hapticFeedback.collectAsStateWithLifecycle(true)
    val autoHideGaming by viewModel.autoHideGaming.collectAsStateWithLifecycle(false)
    val showLabels by viewModel.showLabels.collectAsStateWithLifecycle(true)
    val panelAlpha by viewModel.panelAlpha.collectAsStateWithLifecycle(0.85f)
    val iconSize by viewModel.iconSize.collectAsStateWithLifecycle(48)
    val floatingWindows by viewModel.floatingWindows.collectAsStateWithLifecycle(true)
    val side by viewModel.side.collectAsStateWithLifecycle(FloatingLauncherEdge.Right)
    val handleY by viewModel.handleY.collectAsStateWithLifecycle(0.5f)
    val handleHeight by viewModel.handleHeight.collectAsStateWithLifecycle(72)
    val fileDock by viewModel.fileDock.collectAsStateWithLifecycle(true)

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
                Preference(
                    title = stringResource(R.string.preference_floating_launcher_edit),
                    summary = stringResource(R.string.preference_floating_launcher_edit_summary),
                    enabled = enabled && hasOverlayPermission == true,
                    onClick = {
                        // the editor is part of the sidebar service
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, FloatingLauncherService::class.java)
                                .setAction(FloatingLauncherService.ACTION_OPEN_EDITOR),
                        )
                    },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_floating_bar)) {
                Text(
                    text = stringResource(R.string.preference_floating_launcher_preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                TabPreview(side = side, y = handleY, height = handleHeight, color = color, alpha = if (hideIndicator) 0f else alpha)
                ListPreference(
                    title = stringResource(R.string.preference_floating_launcher_side),
                    items = listOf(
                        ListPreferenceItem(stringResource(R.string.preference_floating_launcher_side_left), FloatingLauncherEdge.Left),
                        ListPreferenceItem(stringResource(R.string.preference_floating_launcher_side_right), FloatingLauncherEdge.Right),
                    ),
                    value = side,
                    onValueChanged = { viewModel.setSide(it) },
                )
                SliderPreference(
                    title = stringResource(R.string.preference_floating_launcher_position),
                    value = (handleY * 100).roundToInt(),
                    min = 5,
                    max = 95,
                    step = 5,
                    onValueChanged = { viewModel.setHandleY(it / 100f) },
                )
                SliderPreference(
                    title = stringResource(R.string.preference_floating_launcher_handle_height),
                    value = handleHeight,
                    min = 48,
                    max = 160,
                    step = 8,
                    onValueChanged = { viewModel.setHandleHeight(it) },
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
                    onValueChanged = { viewModel.setColor((it ?: Color(0xFF9E9E9E)).toArgb()) },
                )
                SliderPreference(
                    title = stringResource(R.string.preference_floating_launcher_alpha),
                    value = alpha,
                    min = 0.1f,
                    max = 1f,
                    onValueChanged = { viewModel.setAlpha(it) },
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
            PreferenceCategory(title = stringResource(R.string.preference_category_sidebar_style)) {
                SwitchPreference(
                    title = stringResource(R.string.preference_floating_launcher_columns),
                    summary = stringResource(R.string.preference_floating_launcher_columns_summary),
                    value = columns == 2,
                    onValueChanged = { viewModel.setColumns(if (it) 2 else 1) },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_floating_launcher_show_labels),
                    summary = stringResource(R.string.preference_floating_launcher_show_labels_summary),
                    value = showLabels,
                    onValueChanged = { viewModel.setShowLabels(it) },
                )
                SliderPreference(
                    title = stringResource(R.string.preference_floating_launcher_panel_alpha),
                    value = panelAlpha,
                    min = 0.3f,
                    max = 1f,
                    onValueChanged = { viewModel.setPanelAlpha(it) },
                )
                SliderPreference(
                    title = stringResource(R.string.preference_floating_launcher_icon_size),
                    value = iconSize,
                    min = 32,
                    max = 72,
                    step = 4,
                    onValueChanged = { viewModel.setIconSize(it) },
                )
                SliderPreference(
                    title = stringResource(R.string.preference_floating_launcher_max_per_column),
                    value = maxPerColumn,
                    min = 3,
                    max = 20,
                    onValueChanged = { viewModel.setMaxPerColumn(it) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_behavior)) {
                SwitchPreference(
                    title = stringResource(R.string.preference_floating_launcher_file_dock),
                    summary = stringResource(R.string.preference_floating_launcher_file_dock_summary),
                    value = fileDock,
                    onValueChanged = { viewModel.setFileDock(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_floating_launcher_floating_windows),
                    summary = stringResource(R.string.preference_floating_launcher_floating_windows_summary),
                    value = floatingWindows,
                    onValueChanged = { viewModel.setFloatingWindows(it) },
                )
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

/** A small preview of where the handle is: the edge, the height on the screen and the look */
@Composable
private fun TabPreview(side: FloatingLauncherEdge, y: Float, height: Int, color: Int, alpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val handleHeight = (height * 0.75f * 160f / 800f).dp.coerceAtLeast(16.dp)
        Box(
            modifier = Modifier
                .align(
                    BiasAlignment(
                        horizontalBias = if (side == FloatingLauncherEdge.Left) -1f else 1f,
                        verticalBias = y * 2f - 1f,
                    )
                )
                .size(width = 4.dp, height = handleHeight)
                .clip(RoundedCornerShape(50))
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
