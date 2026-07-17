package de.mm20.launcher2.ui.settings.desktopmode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.DesktopModeOrientation
import de.mm20.launcher2.ui.R
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
        }
    }
}
