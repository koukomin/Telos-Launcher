package de.mm20.launcher2.ui.settings.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.about.AboutSettingsRoute
import de.mm20.launcher2.ui.settings.appearance.AppearanceSettingsRoute
import de.mm20.launcher2.ui.settings.gestures.GesturesSettingsRoute
import de.mm20.launcher2.ui.settings.homescreen.HomescreenSettingsRoute
import de.mm20.launcher2.ui.settings.icons.IconsSettingsRoute
import de.mm20.launcher2.ui.settings.integrations.IntegrationsSettingsRoute
import de.mm20.launcher2.ui.settings.locale.LocaleSettingsRoute
import de.mm20.launcher2.ui.settings.search.SearchSettingsRoute
import de.mm20.launcher2.ui.settings.advanced.AdvancedSettingsRoute
import de.mm20.launcher2.ui.settings.webapps.WebAppsSettingsRoute
import de.mm20.launcher2.ui.settings.dynamicisland.DynamicIslandSettingsRoute
import de.mm20.launcher2.ui.settings.freeze.SmartFreezeDashboardRoute
import de.mm20.launcher2.ui.settings.floating.FloatingLauncherSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object MainRoute: NavKey

@Composable
fun MainSettingsScreen() {
    val backStack = LocalBackStack.current
    PreferenceScreen(
        title = stringResource(R.string.settings),
    ) {
        item {
            PreferenceCategory {
                Preference(
                    icon = R.drawable.palette_24px,
                    title = stringResource(id = R.string.preference_screen_appearance),
                    summary = stringResource(id = R.string.preference_screen_appearance_summary),
                    onClick = {
                        backStack.add(AppearanceSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.home_24px,
                    title = stringResource(id = R.string.preference_screen_homescreen),
                    summary = stringResource(id = R.string.preference_screen_homescreen_summary),
                    onClick = {
                        backStack.add(HomescreenSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.apps_24px,
                    title = stringResource(id = R.string.preference_screen_icons),
                    summary = stringResource(id = R.string.preference_screen_icons_summary),
                    onClick = {
                        backStack.add(IconsSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.language_24px,
                    title = stringResource(id = R.string.preference_screen_web_app_shortcuts),
                    summary = stringResource(id = R.string.preference_screen_web_app_shortcuts_summary),
                    onClick = {
                        backStack.add(WebAppsSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.search_24px,
                    title = stringResource(id = R.string.preference_screen_search),
                    summary = stringResource(id = R.string.preference_screen_search_summary),
                    onClick = {
                        backStack.add(SearchSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.gesture_24px,
                    title = stringResource(id = R.string.preference_screen_gestures),
                    summary = stringResource(id = R.string.preference_screen_gestures_summary),
                    onClick = {
                        backStack.add(GesturesSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.timer_24px,
                    title = stringResource(id = R.string.preference_screen_dynamic_island),
                    summary = stringResource(id = R.string.preference_screen_dynamic_island_summary),
                    onClick = {
                        backStack.add(DynamicIslandSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.power_24px,
                    title = stringResource(id = R.string.preference_screen_integrations),
                    summary = stringResource(id = R.string.preference_screen_integrations_summary),
                    onClick = {
                        backStack.add(IntegrationsSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.translate_24px,
                    title = stringResource(id = R.string.preference_screen_locale),
                    summary = stringResource(id = R.string.preference_screen_locale_summary),
                    onClick = {
                        backStack.add(LocaleSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.settings_24px,
                    title = stringResource(id = R.string.preference_screen_advanced),
                    summary = stringResource(id = R.string.preference_screen_advanced_summary),
                    onClick = {
                        backStack.add(AdvancedSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.apps_24px,
                    title = stringResource(R.string.hc_floating_launcher),
                    summary = stringResource(R.string.hc_floating_panel_with_quick_access_to_your),
                    onClick = {
                        backStack.add(FloatingLauncherSettingsRoute)
                    }
                )
                // === TELOS_PENDING_REVIEW_START: smart_freeze_ui_and_actions ===
                Preference(
                    icon = R.drawable.ac_unit_24px,
                    title = stringResource(R.string.hc_smart_freeze_dashboard),
                    summary = stringResource(R.string.hc_manage_frozen_apps_and_shizuku_status),
                    onClick = {
                        backStack.add(SmartFreezeDashboardRoute)
                    }
                )
                // === TELOS_PENDING_REVIEW_END: smart_freeze_ui_and_actions ===
                Preference(
                    icon = R.drawable.info_24px,
                    title = stringResource(id = R.string.preference_screen_about),
                    summary = stringResource(id = R.string.preference_screen_about_summary),
                    onClick = {
                        backStack.add(AboutSettingsRoute)
                    }
                )
            }
        }
    }
}
