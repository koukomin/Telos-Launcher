package de.mm20.launcher2.ui.settings.main
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

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
import de.mm20.launcher2.ui.settings.platform.PlatformEnvironment
import kotlinx.serialization.Serializable

@Serializable
data object MainRoute: NavKey

@Composable
fun MainSettingsScreen() {
    val backStack = LocalBackStack.current
    val visibility = PlatformEnvironment.settings(desktopModeOnAndroid = false)
    PreferenceScreen(
        title = stringResource(I18nR.string.settings),
    ) {
        item {
            PreferenceCategory {
                Preference(
                    icon = BaseR.drawable.palette_24px,
                    title = stringResource(id = I18nR.string.preference_screen_appearance),
                    summary = stringResource(id = I18nR.string.preference_screen_appearance_summary),
                    onClick = {
                        backStack.add(AppearanceSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.home_24px,
                    title = stringResource(id = I18nR.string.preference_screen_homescreen),
                    summary = stringResource(id = I18nR.string.preference_screen_homescreen_summary),
                    onClick = {
                        backStack.add(HomescreenSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.apps_24px,
                    title = stringResource(id = I18nR.string.preference_screen_icons),
                    summary = stringResource(id = I18nR.string.preference_screen_icons_summary),
                    onClick = {
                        backStack.add(IconsSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.language_24px,
                    title = stringResource(id = I18nR.string.preference_screen_web_app_shortcuts),
                    summary = stringResource(id = I18nR.string.preference_screen_web_app_shortcuts_summary),
                    onClick = {
                        backStack.add(WebAppsSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.search_24px,
                    title = stringResource(id = I18nR.string.preference_screen_search),
                    summary = stringResource(id = I18nR.string.preference_screen_search_summary),
                    onClick = {
                        backStack.add(SearchSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.gesture_24px,
                    title = stringResource(id = I18nR.string.preference_screen_gestures),
                    summary = stringResource(id = I18nR.string.preference_screen_gestures_summary),
                    onClick = {
                        backStack.add(GesturesSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.timer_24px,
                    title = stringResource(id = I18nR.string.preference_screen_dynamic_island),
                    summary = stringResource(id = I18nR.string.preference_screen_dynamic_island_summary),
                    onClick = {
                        backStack.add(DynamicIslandSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.power_24px,
                    title = stringResource(id = I18nR.string.preference_screen_integrations),
                    summary = stringResource(id = I18nR.string.preference_screen_integrations_summary),
                    onClick = {
                        backStack.add(IntegrationsSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.translate_24px,
                    title = stringResource(id = I18nR.string.preference_screen_locale),
                    summary = stringResource(id = I18nR.string.preference_screen_locale_summary),
                    onClick = {
                        backStack.add(LocaleSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.settings_24px,
                    title = stringResource(id = I18nR.string.preference_screen_advanced),
                    summary = stringResource(id = I18nR.string.preference_screen_advanced_summary),
                    onClick = {
                        backStack.add(AdvancedSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.widgets_24px,
                    title = stringResource(I18nR.string.preference_screen_floating_launcher),
                    summary = stringResource(I18nR.string.preference_screen_floating_launcher_summary),
                    onClick = {
                        backStack.add(FloatingLauncherSettingsRoute)
                    }
                )
                // === TELOS_PENDING_REVIEW_START: smart_freeze_ui_and_actions ===
                if (visibility.showShizukuDhizukuFreeze) {
                    Preference(
                        icon = BaseR.drawable.ac_unit_24px,
                        title = stringResource(I18nR.string.preference_screen_freeze),
                        summary = stringResource(I18nR.string.preference_screen_freeze_summary),
                        onClick = {
                            backStack.add(SmartFreezeDashboardRoute)
                        }
                    )
                }
                // === TELOS_PENDING_REVIEW_END: smart_freeze_ui_and_actions ===
                Preference(
                    icon = BaseR.drawable.info_24px,
                    title = stringResource(id = I18nR.string.preference_screen_about),
                    summary = stringResource(id = I18nR.string.preference_screen_about_summary),
                    onClick = {
                        backStack.add(AboutSettingsRoute)
                    }
                )
            }
        }
    }
}
