package de.mm20.launcher2.preferences.weather

import android.icu.util.LocaleData
import android.icu.util.ULocale
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.LatLon
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.ProviderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

sealed interface WeatherLocation {
    val name: String

    data class LatLon(
        override val name: String,
        val lat: Double,
        val lon: Double,
    ) : WeatherLocation

    data class Id(
        override val name: String,
        val locationId: String,
    ) : WeatherLocation

    data object Managed : WeatherLocation {
        override val name: String = "Managed by plugin"
    }
}

data class WeatherSettingsData(
    val provider: String = "metno",
    val autoLocation: Boolean = true,
    val location: LatLon? = null,
    val locationName: String? = null,
    val lastLocation: LatLon? = null,
    val lastUpdate: Long = 0L,
    val providerSettings: Map<String, ProviderSettings> = emptyMap(),
)

/** What the weather alerts watch for, see [WeatherSettings.alerts] */
data class WeatherAlertConfig(
    val enabled: Boolean = false,
    val rain: Boolean = true,
    val rainProbability: Int = 70,
    val heavyRain: Boolean = true,
    val snow: Boolean = true,
    val thunder: Boolean = true,
    val heat: Boolean = true,
    val heatTemperature: Int = 35,
    val frost: Boolean = true,
    val frostTemperature: Int = 0,
    val wind: Boolean = true,
    val windSpeed: Int = 60,
    val uv: Boolean = false,
    val uvIndex: Int = 8,
    val hours: Int = 24,
)

class WeatherSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) :
    Flow<WeatherSettingsData> by (
            launcherDataStore.data.map {
                WeatherSettingsData(
                    provider = it.weather.weatherProvider,
                    autoLocation = it.weather.weatherAutoLocation,
                    location = it.weather.weatherLocation,
                    locationName = it.weather.weatherLocationName,
                    lastLocation = it.weather.weatherLastLocation,
                    lastUpdate = it.weather.weatherLastUpdate,
                    providerSettings = it.weather.weatherProviderSettings,
                )
            }.distinctUntilChanged()
            ) {

    val location = launcherDataStore.data.map {
        val providerSettings = it.weather.weatherProviderSettings[it.weather.weatherProvider]

        if (providerSettings?.managedLocation == true) {
            return@map WeatherLocation.Managed
        }
        val id = providerSettings?.locationId
        val name = providerSettings?.locationName

        if (id != null && name != null) {
            WeatherLocation.Id(name, id)
        } else if (it.weather.weatherLocation != null && it.weather.weatherLocationName != null) {
            WeatherLocation.LatLon(
                it.weather.weatherLocationName,
                it.weather.weatherLocation.lat,
                it.weather.weatherLocation.lon
            )
        } else {
            null
        }
    }.distinctUntilChanged()

    val autoLocation = launcherDataStore.data.map { it.weather.weatherAutoLocation }
        .distinctUntilChanged()

    fun setLocation(location: WeatherLocation) {
        launcherDataStore.update {
            val providerSettings =
                it.weather.weatherProviderSettings.getOrDefault(it.weather.weatherProvider, ProviderSettings())
            when (location) {
                is WeatherLocation.LatLon -> {
                    it.copy(
                        weather = it.weather.copy(
                            weatherLocation = LatLon(lat = location.lat, lon = location.lon),
                            weatherLocationName = location.name,
                            weatherLastUpdate = 0L,
                            weatherAutoLocation = false,
                            weatherProviderSettings = it.weather.weatherProviderSettings.toMutableMap().apply {
                                put(
                                    it.weather.weatherProvider,
                                    providerSettings.copy(
                                        locationId = null,
                                        locationName = null,
                                        managedLocation = false,
                                    )
                                )
                            }
                        )
                    )
                }

                is WeatherLocation.Id -> {
                    it.copy(
                        weather = it.weather.copy(
                            weatherLocation = null,
                            weatherLocationName = null,
                            weatherAutoLocation = false,
                            weatherLastUpdate = 0L,
                            weatherProviderSettings = it.weather.weatherProviderSettings.toMutableMap().apply {
                                put(
                                    it.weather.weatherProvider,
                                    providerSettings.copy(
                                        locationId = location.locationId,
                                        locationName = location.name,
                                        managedLocation = false,
                                    )
                                )
                            }
                        )
                    )
                }

                is WeatherLocation.Managed -> {
                    it.copy(
                        weather = it.weather.copy(
                            weatherLocation = null,
                            weatherLocationName = null,
                            weatherAutoLocation = true,
                            weatherLastUpdate = 0L,
                            weatherProviderSettings = it.weather.weatherProviderSettings.toMutableMap().apply {
                                put(
                                    it.weather.weatherProvider,
                                    providerSettings.copy(
                                        locationId = null,
                                        locationName = null,
                                        managedLocation = true,
                                    )
                                )
                            }
                        )
                    )
                }
            }
        }
    }

    fun setLastLocation(location: LatLon) {
        launcherDataStore.update {
            it.copy(
                weather = it.weather.copy(weatherLastLocation = location),
            )
        }
    }

    val lastUpdate = launcherDataStore.data.map { it.weather.weatherLastUpdate }
        .distinctUntilChanged()

    fun setLastUpdate(lastUpdate: Long) {
        launcherDataStore.update {
            it.copy(weather = it.weather.copy(weatherLastUpdate = lastUpdate))
        }
    }

    val providerId = launcherDataStore.data.map { it.weather.weatherProvider }
        .distinctUntilChanged()

    fun setProvider(provider: String) {
        launcherDataStore.update {
            it.copy(
                weather = it.weather.copy(
                    weatherProvider = provider,
                    weatherLastUpdate = 0L,
                )
            )
        }
    }

    fun setAutoLocation(autoLocation: Boolean) {
        launcherDataStore.update {
            it.copy(
                weather = it.weather.copy(
                    weatherAutoLocation = autoLocation,
                    weatherLastUpdate = 0L,
                )
            )
        }
    }

    /** The settings of the notifications for severe weather */
    val alerts = launcherDataStore.data.map {
        val w = it.weather
        WeatherAlertConfig(
            enabled = w.weatherAlertsEnabled,
            rain = w.weatherAlertRain,
            rainProbability = w.weatherAlertRainProbability,
            heavyRain = w.weatherAlertHeavyRain,
            snow = w.weatherAlertSnow,
            thunder = w.weatherAlertThunder,
            heat = w.weatherAlertHeat,
            heatTemperature = w.weatherAlertHeatTemperature,
            frost = w.weatherAlertFrost,
            frostTemperature = w.weatherAlertFrostTemperature,
            wind = w.weatherAlertWind,
            windSpeed = w.weatherAlertWindSpeed,
            uv = w.weatherAlertUv,
            uvIndex = w.weatherAlertUvIndex,
            hours = w.weatherAlertHours,
        )
    }.distinctUntilChanged()

    fun updateAlerts(block: (WeatherAlertConfig) -> WeatherAlertConfig) {
        launcherDataStore.update {
            val w = it.weather
            val c = block(
                WeatherAlertConfig(
                    w.weatherAlertsEnabled, w.weatherAlertRain, w.weatherAlertRainProbability, w.weatherAlertHeavyRain,
                    w.weatherAlertSnow, w.weatherAlertThunder, w.weatherAlertHeat, w.weatherAlertHeatTemperature,
                    w.weatherAlertFrost, w.weatherAlertFrostTemperature, w.weatherAlertWind, w.weatherAlertWindSpeed,
                    w.weatherAlertUv, w.weatherAlertUvIndex, w.weatherAlertHours,
                )
            )
            it.copy(
                weather = w.copy(
                    weatherAlertsEnabled = c.enabled,
                    weatherAlertRain = c.rain,
                    weatherAlertRainProbability = c.rainProbability.coerceIn(10, 100),
                    weatherAlertHeavyRain = c.heavyRain,
                    weatherAlertSnow = c.snow,
                    weatherAlertThunder = c.thunder,
                    weatherAlertHeat = c.heat,
                    weatherAlertHeatTemperature = c.heatTemperature.coerceIn(20, 55),
                    weatherAlertFrost = c.frost,
                    weatherAlertFrostTemperature = c.frostTemperature.coerceIn(-30, 10),
                    weatherAlertWind = c.wind,
                    weatherAlertWindSpeed = c.windSpeed.coerceIn(20, 150),
                    weatherAlertUv = c.uv,
                    weatherAlertUvIndex = c.uvIndex.coerceIn(3, 12),
                    weatherAlertHours = c.hours.coerceIn(3, 72),
                )
            )
        }
    }

    val alertLastSent = launcherDataStore.data.map { it.weather.weatherAlertLastSent }.distinctUntilChanged()

    fun setAlertSent(type: String, time: Long) {
        launcherDataStore.update {
            it.copy(weather = it.weather.copy(weatherAlertLastSent = it.weather.weatherAlertLastSent + (type to time)))
        }
    }

    val measurementSystem = launcherDataStore.data.map {
        it.locale.localeMeasurementSystem
    }.distinctUntilChanged()

    val timeFormat = launcherDataStore.data.map {
        it.locale.localeTimeFormat
    }.distinctUntilChanged()
}
