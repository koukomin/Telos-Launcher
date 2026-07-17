package de.mm20.launcher2.weather.openmeteo

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.weather.WeatherLocation
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.GeocoderWeatherProvider
import de.mm20.launcher2.weather.R

/**
 * Open-Meteo (https://open-meteo.com/) - the default weather provider: free for non-commercial
 * use, no API key or contact-info configuration needed (unlike MetNo/OpenWeatherMap in this
 * build), worldwide coverage. Replaces BrightSky/DWD (Germany-only) as the default; BrightSky is
 * kept as an option for users who specifically want official DWD data.
 */
internal class OpenMeteoProvider(
    private val context: Context,
) : GeocoderWeatherProvider(context) {
    private val api = OpenMeteoApi()

    override suspend fun getWeatherData(location: WeatherLocation): List<Forecast>? {
        return when (location) {
            is WeatherLocation.LatLon -> getWeatherData(location.lat, location.lon, location.name)
            else -> {
                Log.e("OpenMeteoProvider", "Unsupported location type: $location")
                null
            }
        }
    }

    override suspend fun getWeatherData(lat: Double, lon: Double): List<Forecast>? {
        val locationName = getLocationName(lat, lon)
        return getWeatherData(lat, lon, locationName)
    }

    private suspend fun getWeatherData(
        lat: Double,
        lon: Double,
        locationName: String,
    ): List<Forecast>? {
        val result = runCatching {
            api.forecast(lat, lon)
        }.getOrElse {
            CrashReporter.logException(Exception(it))
            return null
        }
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm")
        format.timeZone = TimeZone.getTimeZone("UTC")
        val hourly = result.hourly
        val updateTime = System.currentTimeMillis()
        val forecasts = mutableListOf<Forecast>()
        for (i in hourly.time.indices) {
            val timestamp = format.parse(hourly.time.getOrNull(i))?.time ?: continue
            val temperatureC = hourly.temperature2m.getOrNull(i) ?: continue
            val code = hourly.weatherCode.getOrNull(i) ?: continue
            forecasts.add(
                Forecast(
                    timestamp = timestamp,
                    temperature = temperatureC + 273.15,
                    pressure = hourly.pressureMsl.getOrNull(i),
                    humidity = hourly.relativeHumidity2m.getOrNull(i),
                    icon = iconForCode(code),
                    condition = conditionForCode(code),
                    clouds = hourly.cloudCover.getOrNull(i),
                    windSpeed = hourly.windSpeed10m.getOrNull(i),
                    windDirection = hourly.windDirection10m.getOrNull(i),
                    precipitation = hourly.precipitation.getOrNull(i),
                    precipProbability = hourly.precipitationProbability.getOrNull(i),
                    uvIndex = hourly.uvIndex.getOrNull(i),
                    night = hourly.isDay.getOrNull(i) == 0,
                    location = locationName,
                    provider = "Open-Meteo",
                    providerUrl = "https://open-meteo.com/",
                    updateTime = updateTime,
                )
            )
        }
        return forecasts
    }

    /** Maps Open-Meteo's WMO weather codes (https://open-meteo.com/en/docs, "WMO Weather
     * interpretation codes") onto the icon set every other provider in this module uses. */
    private fun iconForCode(code: Int): Int {
        return when (code) {
            0, 1 -> Forecast.CLEAR
            2 -> Forecast.PARTLY_CLOUDY
            3 -> Forecast.OVERCAST
            45, 48 -> Forecast.FOG
            51, 61, 80 -> Forecast.LIGHT_RAIN
            53, 63, 81 -> Forecast.RAIN
            55, 65, 82 -> Forecast.HEAVY_RAIN
            56, 57, 66, 67 -> Forecast.SLEET
            71, 73, 75, 77, 85, 86 -> Forecast.SNOW
            95, 96, 99 -> Forecast.THUNDERSTORM
            else -> Forecast.UNKNOWN
        }
    }

    private fun conditionForCode(code: Int): String {
        val resId = when (code) {
            0, 1 -> R.string.weather_condition_clearsky
            2 -> R.string.weather_condition_partlycloudy
            3 -> R.string.weather_condition_cloudy
            45, 48 -> R.string.weather_condition_fog
            51, 61, 80 -> R.string.weather_condition_lightrain
            53, 63, 81 -> R.string.weather_condition_rain
            55, 65, 82 -> R.string.weather_condition_heavyrain
            56, 57, 66, 67 -> R.string.weather_condition_sleet
            71, 73, 75, 77, 85, 86 -> R.string.weather_condition_snow
            95, 96, 99 -> R.string.weather_condition_thunderstorm
            else -> R.string.weather_condition_unknown
        }
        return context.getString(resId)
    }

    companion object {
        internal const val Id = "openmeteo"
    }
}
