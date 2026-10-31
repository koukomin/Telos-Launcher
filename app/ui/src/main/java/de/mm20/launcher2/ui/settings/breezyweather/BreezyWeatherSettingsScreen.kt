package de.mm20.launcher2.ui.settings.breezyweather
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object BreezyWeatherSettingsRoute: NavKey

@Composable
fun BreezyWeatherSettingsScreen() {
    val activity = LocalActivity.current as AppCompatActivity
    val viewModel: BreezyWeatherSettingsScreenVM = viewModel()

    val isBreezyInstalled by viewModel.isBreezyInstalled.collectAsStateWithLifecycle(null)
    val isWeatherProvider by viewModel.isBreezySetAsWeatherProvider.collectAsStateWithLifecycle(null)

    PreferenceScreen(
        title = stringResource(I18nR.string.preference_breezyweather_integration)
    ) {
        if (isBreezyInstalled == false) {
            item {
                PreferenceCategory {
                    Banner(
                        text = stringResource(
                            I18nR.string.preference_breezyweather_integration_description,
                            stringResource(BaseR.string.app_name),
                        ),
                        icon = BaseR.drawable.info_24px,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
                        primaryAction = {
                            Button(onClick = {
                                viewModel.downloadBreezyApp(activity)
                            }) {
                                Text(stringResource(I18nR.string.action_install))
                            }
                        }
                    )
                }
            }
        }
        if (isBreezyInstalled == true) {
            item {
                PreferenceCategory {
                    Banner(
                        text = stringResource(
                            I18nR.string.preference_breezyweather_integration_instructions,
                            stringResource(BaseR.string.app_name),
                        ),
                        icon = BaseR.drawable.info_24px,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    )
                    Preference(
                        title = stringResource(I18nR.string.preference_breezyweather_integration),
                        summary = stringResource(
                            if (isWeatherProvider == true) I18nR.string.plugin_weather_provider_enabled
                            else I18nR.string.plugin_weather_provider_enable
                        ),
                        enabled = isWeatherProvider == false,
                        onClick = {
                            viewModel.setBreezyAsWeatherProvider()
                        }
                    )
                    Preference(
                        title = stringResource(I18nR.string.preference_launch_breezyweather_app),
                        icon = BaseR.drawable.open_in_new_24px,
                        onClick = {
                            viewModel.launchBreezyApp(activity)
                        }
                    )
                }
            }
        }
    }
}