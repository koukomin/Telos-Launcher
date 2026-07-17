package de.mm20.launcher2.ui.settings.desktopmode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.DesktopModeOrientation
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.TextPreference
import kotlinx.serialization.Serializable

@Serializable
data object DesktopModeSettingsRoute : NavKey

@Composable
fun DesktopModeSettingsScreen() {
    val viewModel: DesktopModeSettingsScreenVM = viewModel(factory = DesktopModeSettingsScreenVM.Factory)

    val enabled by viewModel.enabled.collectAsStateWithLifecycle(false)
    val orientation by viewModel.orientation.collectAsStateWithLifecycle(DesktopModeOrientation.Auto)
    val externalDisplay by viewModel.externalDisplayConnected.collectAsStateWithLifecycle(null)
    val freeformPreferenceEnabled by viewModel.freeformPreferenceEnabled.collectAsStateWithLifecycle(false)
    val freeformActiveInSystem by viewModel.freeformActiveInSystem.collectAsStateWithLifecycle(false)
    val freeformShizukuUnavailable by viewModel.freeformShizukuUnavailable.collectAsStateWithLifecycle(false)
    val freeformShizukuPermissionDenied by viewModel.freeformShizukuPermissionDenied.collectAsStateWithLifecycle(false)

    PreferenceScreen(title = stringResource(R.string.preference_screen_desktop_mode)) {
        item {
            PreferenceCategory {
                if (!viewModel.isSupportedOnThisDevice) {
                    TextPreference(
                        title = stringResource(R.string.desktop_mode_unsupported),
                        value = stringResource(R.string.desktop_mode_unsupported_summary),
                        summary = stringResource(R.string.desktop_mode_unsupported_summary),
                        enabled = false,
                        onValueChanged = {},
                    )
                } else {
                    SwitchPreference(
                        title = stringResource(R.string.preference_desktop_mode_enabled),
                        summary = stringResource(R.string.preference_desktop_mode_enabled_summary),
                        value = enabled,
                        onValueChanged = { viewModel.setEnabled(it) },
                    )
                    TextPreference(
                        title = stringResource(R.string.desktop_mode_display_status),
                        value = stringResource(
                            if (externalDisplay != null) R.string.desktop_mode_display_connected
                            else R.string.desktop_mode_display_not_connected
                        ),
                        summary = stringResource(
                            if (externalDisplay != null) R.string.desktop_mode_display_connected
                            else R.string.desktop_mode_display_not_connected
                        ),
                        enabled = false,
                        onValueChanged = {},
                    )
                }
            }
        }
        if (enabled && viewModel.isSupportedOnThisDevice) {
            item {
                PreferenceCategory(title = stringResource(R.string.preference_category_appearance)) {
                    ListPreference(
                        title = stringResource(R.string.desktop_mode_orientation),
                        items = listOf(
                            ListPreferenceItem(
                                stringResource(R.string.desktop_mode_orientation_auto),
                                DesktopModeOrientation.Auto
                            ),
                            ListPreferenceItem(
                                stringResource(R.string.desktop_mode_orientation_portrait),
                                DesktopModeOrientation.Portrait
                            ),
                            ListPreferenceItem(
                                stringResource(R.string.desktop_mode_orientation_landscape),
                                DesktopModeOrientation.Landscape
                            ),
                        ),
                        value = orientation,
                        onValueChanged = { viewModel.setOrientation(it) },
                    )
                }
            }
            item {
                PreferenceCategory(title = stringResource(R.string.desktop_mode_window_management_category)) {
                    if (!viewModel.isFreeformPotentiallySupported) {
                        TextPreference(
                            title = stringResource(R.string.desktop_mode_freeform_unsupported),
                            value = stringResource(R.string.desktop_mode_freeform_unsupported_summary),
                            summary = stringResource(R.string.desktop_mode_freeform_unsupported_summary),
                            enabled = false,
                            onValueChanged = {},
                        )
                    } else {
                        SwitchPreference(
                            title = stringResource(R.string.desktop_mode_freeform_enabled),
                            summary = stringResource(
                                if (freeformPreferenceEnabled && freeformActiveInSystem)
                                    R.string.desktop_mode_freeform_enabled_summary_active
                                else if (freeformPreferenceEnabled)
                                    R.string.desktop_mode_freeform_enabled_summary_pending
                                else
                                    R.string.desktop_mode_freeform_enabled_summary
                            ),
                            value = freeformPreferenceEnabled,
                            onValueChanged = { viewModel.setFreeformEnabled(it) },
                        )
                        AnimatedVisibility(freeformPreferenceEnabled) {
                            Banner(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                text = stringResource(R.string.desktop_mode_freeform_warning),
                                icon = R.drawable.error_24px,
                            )
                        }
                        AnimatedVisibility(freeformShizukuUnavailable) {
                            Banner(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                text = stringResource(R.string.desktop_mode_freeform_shizuku_unavailable),
                                icon = R.drawable.error_24px,
                            )
                        }
                        AnimatedVisibility(freeformShizukuPermissionDenied) {
                            Banner(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                text = stringResource(R.string.desktop_mode_freeform_shizuku_denied),
                                icon = R.drawable.error_24px,
                            )
                        }
                    }
                }
            }
        }
    }
}
