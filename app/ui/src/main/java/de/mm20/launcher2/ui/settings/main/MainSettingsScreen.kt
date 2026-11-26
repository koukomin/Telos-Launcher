package de.mm20.launcher2.ui.settings.main

import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
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
import de.mm20.launcher2.ui.system.SystemDashboardRoute
import de.mm20.launcher2.ui.settings.webapps.WebAppsSettingsRoute
import de.mm20.launcher2.ui.settings.dynamicisland.DynamicIslandSettingsRoute
import de.mm20.launcher2.ui.settings.floating.FloatingLauncherSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object MainRoute: NavKey

private data class SettingItem(
    val icon: Int,
    val titleRes: Int,
    val summaryRes: Int,
    val route: NavKey
)

@Composable
fun MainSettingsScreen() {
    val backStack = LocalBackStack.current
    var query by rememberSaveable { mutableStateOf("") }

    val items = listOf(
        SettingItem(BaseR.drawable.palette_24px, I18nR.string.preference_screen_appearance, I18nR.string.preference_screen_appearance_summary, AppearanceSettingsRoute),
        SettingItem(BaseR.drawable.home_24px, I18nR.string.preference_screen_homescreen, I18nR.string.preference_screen_homescreen_summary, HomescreenSettingsRoute),
        SettingItem(BaseR.drawable.apps_24px, I18nR.string.preference_screen_icons, I18nR.string.preference_screen_icons_summary, IconsSettingsRoute),
        SettingItem(BaseR.drawable.language_24px, I18nR.string.preference_screen_web_app_shortcuts, I18nR.string.preference_screen_web_app_shortcuts_summary, WebAppsSettingsRoute),
        SettingItem(BaseR.drawable.search_24px, I18nR.string.preference_screen_search, I18nR.string.preference_screen_search_summary, SearchSettingsRoute),
        SettingItem(BaseR.drawable.gesture_24px, I18nR.string.preference_screen_gestures, I18nR.string.preference_screen_gestures_summary, GesturesSettingsRoute),
        SettingItem(BaseR.drawable.timer_24px, I18nR.string.preference_screen_dynamic_island, I18nR.string.preference_screen_dynamic_island_summary, DynamicIslandSettingsRoute),
        SettingItem(BaseR.drawable.power_24px, I18nR.string.preference_screen_integrations, I18nR.string.preference_screen_integrations_summary, IntegrationsSettingsRoute),
        SettingItem(BaseR.drawable.translate_24px, I18nR.string.preference_screen_locale, I18nR.string.preference_screen_locale_summary, LocaleSettingsRoute),
        SettingItem(BaseR.drawable.settings_24px, I18nR.string.telos_system_label, I18nR.string.preference_screen_advanced_summary, SystemDashboardRoute()),
        SettingItem(BaseR.drawable.widgets_24px, I18nR.string.preference_screen_floating_launcher, I18nR.string.preference_screen_floating_launcher_summary, FloatingLauncherSettingsRoute),
        SettingItem(BaseR.drawable.info_24px, I18nR.string.preference_screen_about, I18nR.string.preference_screen_about_summary, AboutSettingsRoute)
    )

    PreferenceScreen(
        title = stringResource(I18nR.string.settings),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(I18nR.string.search_bar_placeholder)) },
                leadingIcon = { Icon(painterResource(BaseR.drawable.search_24px), contentDescription = null) },
                singleLine = true
            )
        }
        item {
            PreferenceCategory {
                val filtered = items.filter { item ->
                    if (query.isBlank()) true
                    else {
                        val title = stringResource(item.titleRes)
                        val summary = stringResource(item.summaryRes)
                        title.contains(query, ignoreCase = true) || summary.contains(query, ignoreCase = true)
                    }
                }
                for (item in filtered) {
                    Preference(
                        icon = item.icon,
                        title = stringResource(id = item.titleRes),
                        summary = stringResource(id = item.summaryRes),
                        onClick = {
                            backStack.add(item.route)
                        }
                    )
                }
            }
        }
    }
}
