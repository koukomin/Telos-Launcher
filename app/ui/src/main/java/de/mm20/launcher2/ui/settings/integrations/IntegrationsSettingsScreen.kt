package de.mm20.launcher2.ui.settings.integrations
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.FeatureFlags
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.breezyweather.BreezyWeatherSettingsRoute
import de.mm20.launcher2.ui.settings.feed.FeedIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.media.MediaIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.nextcloud.NextcloudSettingsRoute
import de.mm20.launcher2.ui.settings.owncloud.OwncloudSettingsRoute
import de.mm20.launcher2.ui.settings.smartspacer.SmartspacerSettingsRoute
import de.mm20.launcher2.ui.settings.tasks.TasksIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.weather.WeatherIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.wikipedia.WikipediaSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object IntegrationsSettingsRoute : NavKey

@Composable
fun IntegrationsSettingsScreen() {
    val viewModel: IntegrationsSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current

    PreferenceScreen(title = stringResource(I18nR.string.preference_screen_integrations)) {
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(I18nR.string.preference_weather_integration),
                    icon = BaseR.drawable.light_mode_24px,
                    onClick = {
                        backStack.add(WeatherIntegrationSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(I18nR.string.preference_media_integration),
                    icon = BaseR.drawable.play_circle_24px,
                    onClick = {
                        backStack.add(MediaIntegrationSettingsRoute)
                    }
                )
                if (FeatureFlags.feed) {
                    Preference(
                        title = stringResource(I18nR.string.preference_feed_integration),
                        icon = BaseR.drawable.news_24px,
                        onClick = {
                            backStack.add(FeedIntegrationSettingsRoute)
                        }
                    )
                }
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(I18nR.string.hc_cloud_and_network_storage),
                    summary = stringResource(I18nR.string.hf_settings_cloud_storage_summary),
                    icon = BaseR.drawable.cloud_20px,
                    onClick = {
                        backStack.add(de.mm20.launcher2.ui.files.remote.ConnectionsRoute)
                    }
                )
                Preference(
                    title = stringResource(I18nR.string.preference_nextcloud),
                    icon = BaseR.drawable.nextcloud,
                    onClick = {
                        backStack.add(NextcloudSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(I18nR.string.preference_owncloud),
                    icon = BaseR.drawable.owncloud,
                    onClick = {
                        backStack.add(OwncloudSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(I18nR.string.preference_search_wikipedia),
                    icon = BaseR.drawable.wikipedia,
                    onClick = {
                        backStack.add(WikipediaSettingsRoute)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(I18nR.string.preference_tasks_integration),
                    icon = BaseR.drawable.check_24px_sharp,
                    onClick = {
                        backStack.add(TasksIntegrationSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(I18nR.string.preference_breezyweather_integration),
                    icon = BaseR.drawable.breezy_weather,
                    onClick = {
                        backStack.add(BreezyWeatherSettingsRoute)
                    }
                )
                if (isAtLeastApiLevel(29)) {
                    if (FeatureFlags.smartspacerIntegration) {
                        Preference(
                            title = stringResource(I18nR.string.preference_smartspacer_integration),
                            icon = BaseR.drawable.smartspacer,
                            onClick = {
                                backStack.add(SmartspacerSettingsRoute)
                            }
                        )
                    }
                }
            }
        }
    }
}