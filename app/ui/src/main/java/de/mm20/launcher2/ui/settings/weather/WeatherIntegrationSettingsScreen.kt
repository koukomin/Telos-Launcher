package de.mm20.launcher2.ui.settings.weather

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.sendWithBackgroundPermission
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.ui.BuildConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.WeatherLocationSearchDialog
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.locale.LocaleSettingsRoute
import de.mm20.launcher2.weather.breezy.BreezyWeatherProvider
import kotlinx.serialization.Serializable

@Serializable
data object WeatherIntegrationSettingsRoute: NavKey

@Composable
fun WeatherIntegrationSettingsScreen() {
    val viewModel: WeatherIntegrationSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    val availableProviders by viewModel.availableProviders.collectAsState(emptyList())
    val weatherProvider by viewModel.weatherProvider.collectAsState()

    val selectedProviderInfo by remember {
        derivedStateOf { availableProviders.find { it.id == weatherProvider } }
    }

    val pluginState by viewModel.weatherProviderPluginState.collectAsStateWithLifecycle(
        null,
        minActiveState = Lifecycle.State.RESUMED
    )

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_weatherwidget),
        helpUrl = "https://koukomin.github.io/Telos-Launcher/docs/user-guide/integrations/weather"
    ) {
        item {
            PreferenceCategory {
                val state = pluginState?.state
                if (state is PluginState.SetupRequired) {
                    Banner(
                        modifier = Modifier.padding(16.dp),
                        text = state.message
                            ?: stringResource(R.string.plugin_state_setup_required),
                        icon = R.drawable.error_24px,
                        primaryAction = {
                            TextButton(onClick = {
                                try {
                                    state.setupActivity.sendWithBackgroundPermission(context)
                                } catch (e: PendingIntent.CanceledException) {
                                    CrashReporter.logException(e)
                                }
                            }) {
                                Text(stringResource(R.string.plugin_action_setup))
                            }
                        }
                    )
                }
                ListPreference(
                    title = stringResource(R.string.preference_weather_provider),
                    items = availableProviders.map {
                        it.name to it.id
                    },
                    onValueChanged = {
                        if (it != null) viewModel.setWeatherProvider(it)
                    },
                    value = weatherProvider
                )
            }

        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_location)) {
                if (selectedProviderInfo?.id == BreezyWeatherProvider.Id) {
                    Preference(
                        title = stringResource(R.string.preference_location),
                        summary = stringResource(R.string.preference_location_breezy),
                        onClick = {
                            val intent =
                                context.packageManager.getLaunchIntentForPackage("org.breezyweather")
                                    ?: return@Preference
                            context.tryStartActivity(intent)
                        }
                    )
                } else if (selectedProviderInfo?.managedLocation == true) {
                    Preference(
                        title = stringResource(R.string.preference_location_managed),
                        summary = stringResource(R.string.preference_location_managed_summary),
                        enabled = false
                    )
                } else {
                    val hasPermission by viewModel.hasLocationPermission.collectAsState()
                    val autoLocation by viewModel.autoLocation.collectAsState()
                    GuardedPreference(
                        locked = hasPermission == false,
                        description = stringResource(R.string.missing_permission_auto_location),
                        onUnlock = {
                            viewModel.requestLocationPermission(context as AppCompatActivity)
                        }
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.preference_automatic_location),
                            summary = stringResource(R.string.preference_automatic_location_summary),
                            value = autoLocation,
                            onValueChanged = {
                                viewModel.setAutoLocation(it)
                            }
                        )
                    }
                    val location by viewModel.location.collectAsStateWithLifecycle()
                    LocationPreference(
                        title = stringResource(R.string.preference_location),
                        value = location,
                        enabled = !autoLocation,
                    )
                }
            }
        }
        item {
            val alerts by viewModel.alerts.collectAsState()
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            PreferenceCategory(title = stringResource(R.string.preference_category_weather_alerts)) {
                SwitchPreference(
                    title = stringResource(R.string.preference_weather_alerts),
                    summary = stringResource(R.string.preference_weather_alerts_summary),
                    value = alerts.enabled,
                    onValueChanged = { on ->
                        viewModel.updateAlerts { it.copy(enabled = on) }
                        if (on && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
                if (alerts.enabled) {
                    ListPreference(
                        title = stringResource(R.string.preference_weather_alerts_hours),
                        items = listOf(6, 12, 24, 48, 72).map { ListPreferenceItem(stringResource(R.string.preference_weather_alerts_hours_value, it), it) },
                        value = alerts.hours,
                        onValueChanged = { h -> viewModel.updateAlerts { it.copy(hours = h) } },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_weather_alerts_rain),
                        value = alerts.rain,
                        onValueChanged = { v -> viewModel.updateAlerts { it.copy(rain = v) } },
                    )
                    if (alerts.rain) {
                        SliderPreference(
                            title = stringResource(R.string.preference_weather_alerts_rain_probability, alerts.rainProbability),
                            value = alerts.rainProbability, min = 30, max = 100, step = 10,
                            onValueChanged = { v -> viewModel.updateAlerts { it.copy(rainProbability = v) } },
                        )
                    }
                    SwitchPreference(
                        title = stringResource(R.string.preference_weather_alerts_heavy_rain),
                        value = alerts.heavyRain,
                        onValueChanged = { v -> viewModel.updateAlerts { it.copy(heavyRain = v) } },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_weather_alerts_snow),
                        value = alerts.snow,
                        onValueChanged = { v -> viewModel.updateAlerts { it.copy(snow = v) } },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_weather_alerts_thunder),
                        value = alerts.thunder,
                        onValueChanged = { v -> viewModel.updateAlerts { it.copy(thunder = v) } },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_weather_alerts_heat),
                        value = alerts.heat,
                        onValueChanged = { v -> viewModel.updateAlerts { it.copy(heat = v) } },
                    )
                    if (alerts.heat) {
                        SliderPreference(
                            title = stringResource(R.string.preference_weather_alerts_heat_threshold, alerts.heatTemperature),
                            value = alerts.heatTemperature, min = 25, max = 50, step = 1,
                            onValueChanged = { v -> viewModel.updateAlerts { it.copy(heatTemperature = v) } },
                        )
                    }
                    SwitchPreference(
                        title = stringResource(R.string.preference_weather_alerts_frost),
                        value = alerts.frost,
                        onValueChanged = { v -> viewModel.updateAlerts { it.copy(frost = v) } },
                    )
                    if (alerts.frost) {
                        SliderPreference(
                            title = stringResource(R.string.preference_weather_alerts_frost_threshold, alerts.frostTemperature),
                            value = alerts.frostTemperature, min = -15, max = 5, step = 1,
                            onValueChanged = { v -> viewModel.updateAlerts { it.copy(frostTemperature = v) } },
                        )
                    }
                    SwitchPreference(
                        title = stringResource(R.string.preference_weather_alerts_wind),
                        value = alerts.wind,
                        onValueChanged = { v -> viewModel.updateAlerts { it.copy(wind = v) } },
                    )
                    if (alerts.wind) {
                        SliderPreference(
                            title = stringResource(R.string.preference_weather_alerts_wind_threshold, alerts.windSpeed),
                            value = alerts.windSpeed, min = 30, max = 120, step = 10,
                            onValueChanged = { v -> viewModel.updateAlerts { it.copy(windSpeed = v) } },
                        )
                    }
                    SwitchPreference(
                        title = stringResource(R.string.preference_weather_alerts_uv),
                        value = alerts.uv,
                        onValueChanged = { v -> viewModel.updateAlerts { it.copy(uv = v) } },
                    )
                    if (alerts.uv) {
                        SliderPreference(
                            title = stringResource(R.string.preference_weather_alerts_uv_threshold, alerts.uvIndex),
                            value = alerts.uvIndex, min = 5, max = 11, step = 1,
                            onValueChanged = { v -> viewModel.updateAlerts { it.copy(uvIndex = v) } },
                        )
                    }
                    Preference(
                        title = stringResource(R.string.preference_weather_alerts_test),
                        summary = stringResource(R.string.preference_weather_alerts_test_summary),
                        onClick = {
                            viewModel.sendTestAlert { shown ->
                                if (!shown) Toast.makeText(context, R.string.preference_weather_alerts_test_failed, Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_measurement_system),
                    icon = R.drawable.open_in_new_24px,
                    onClick = {
                        backStack.add(LocaleSettingsRoute)
                    }
                )
            }
        }
        if (BuildConfig.DEBUG) {
            item {
                PreferenceCategory(stringResource(R.string.preference_category_debug)) {
                    Preference(
                        "Clear weather data",
                        summary = "Remove weather data from database",
                        onClick = {
                            viewModel.clearWeatherData()
                        })
                }
            }
        }
    }
}

@Composable
fun LocationPreference(
    title: String,
    value: String?,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(
        title = title,
        summary = value,
        enabled = enabled,
        onClick = {
            showDialog = true
        }
    )
    WeatherLocationSearchDialog(
        expanded = showDialog,
        onDismissRequest = { showDialog = false }
    )
}