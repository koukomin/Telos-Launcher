package de.mm20.launcher2.ui.settings.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.preferences.DrawerStyle
import de.mm20.launcher2.ui.component.DrawerStylePreview
import de.mm20.launcher2.ui.component.preferences.ListPref
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPref
import kotlinx.serialization.Serializable

@Serializable
data object AppSearchSettingsRoute : NavKey

@Composable
fun AppSearchSettingsScreen() {
    val viewModel = viewModel<AppSearchSettingsScreenVM>()

    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val showAppDetails by viewModel.showAppDetails.collectAsStateWithLifecycle()
    val showList by viewModel.showList.collectAsStateWithLifecycle()
    val showListIcons by viewModel.showListIcons.collectAsStateWithLifecycle()
    val moveFrozenAppsToEnd by viewModel.moveFrozenAppsToEnd.collectAsStateWithLifecycle()
    val drawerStyle by viewModel.drawerStyle.collectAsStateWithLifecycle()

    PreferenceScreen(title = stringResource(I18nR.string.preference_search_apps)) {
        item {
            PreferenceCategory(title = stringResource(I18nR.string.drawer_style_title)) {
                ListPref(
                    title = stringResource(I18nR.string.drawer_style_title),
                    summary = stringResource(I18nR.string.drawer_style_summary),
                    value = drawerStyle,
                    items = listOf(
                        ListPreferenceItem(stringResource(I18nR.string.drawer_style_classic), DrawerStyle.Classic),
                        ListPreferenceItem(stringResource(I18nR.string.drawer_style_cyber_matrix), DrawerStyle.CyberMatrix),
                        ListPreferenceItem(stringResource(I18nR.string.drawer_style_holo_portal), DrawerStyle.HoloPortal),
                        ListPreferenceItem(stringResource(I18nR.string.drawer_style_command_deck), DrawerStyle.CommandDeck),
                    ),
                    onValueChanged = viewModel::setDrawerStyle
                )
                DrawerStylePreview(style = drawerStyle)
            }
        }

        item {
            PreferenceCategory {
                SwitchPref(
                    icon = BaseR.drawable.apps_24px,
                    title = stringResource(I18nR.string.preference_search_apps),
                    summary = stringResource(I18nR.string.preference_search_apps_summary),
                    value = allApps == true,
                    onValueChanged = viewModel::setAllApps
                )
                SwitchPref(
                    title = stringResource(I18nR.string.preference_search_apps_show_details),
                    summary = stringResource(I18nR.string.preference_search_apps_show_details_summary),
                    value = showAppDetails == true,
                    onValueChanged = viewModel::setHideAppDetails
                )
            }
        }
        item {
            PreferenceCategory {
                SwitchPref(
                    title = stringResource(I18nR.string.preference_grid_list_style),
                    summary = stringResource(I18nR.string.preference_grid_list_style_summary),
                    value = showList == true,
                    onValueChanged = viewModel::setShowList
                )
                AnimatedVisibility(showList == true) {
                    SwitchPref(
                        title = stringResource(I18nR.string.preference_grid_list_icons),
                        summary = stringResource(I18nR.string.preference_grid_list_icons_summary),
                        value = showListIcons == true,
                        onValueChanged = viewModel::setShowListIcons
                    )
                }
                SwitchPref(
                    title = stringResource(I18nR.string.preference_drawer_frozen_to_end),
                    summary = stringResource(I18nR.string.preference_drawer_frozen_to_end_summary),
                    value = moveFrozenAppsToEnd == true,
                    onValueChanged = viewModel::setMoveFrozenAppsToEnd
                )
            }
        }
    }
}
