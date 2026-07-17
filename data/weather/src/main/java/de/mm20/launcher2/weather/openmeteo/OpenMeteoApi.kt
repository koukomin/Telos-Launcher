package de.mm20.launcher2.weather.openmeteo

import de.mm20.launcher2.serialization.Json
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenMeteoResponse(
    val hourly: Hourly,
) {
    @Serializable
    internal data class Hourly(
        val time: List<String>,
        @SerialName("temperature_2m") val temperature2m: List<Double?>,
        @SerialName("relative_humidity_2m") val relativeHumidity2m: List<Double?>,
        @SerialName("precipitation_probability") val precipitationProbability: List<Int?>,
        val precipitation: List<Double?>,
        @SerialName("weather_code") val weatherCode: List<Int?>,
        @SerialName("cloud_cover") val cloudCover: List<Int?>,
        @SerialName("pressure_msl") val pressureMsl: List<Double?>,
        @SerialName("wind_speed_10m") val windSpeed10m: List<Double?>,
        @SerialName("wind_direction_10m") val windDirection10m: List<Double?>,
        @SerialName("uv_index") val uvIndex: List<Double?>,
        @SerialName("is_day") val isDay: List<Int?>,
    )
}

/**
 * Open-Meteo (https://open-meteo.com/) - free for non-commercial use, no API key, worldwide
 * coverage (blends ECMWF/GFS/ICON and other national models depending on region).
 */
internal class OpenMeteoApi {

    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json.Lenient)
            }
            defaultRequest {
                url("https://api.open-meteo.com/")
            }
        }
    }

    suspend fun forecast(lat: Double, lon: Double): OpenMeteoResponse {
        return httpClient.get {
            url {
                path("v1", "forecast")
                parameter("latitude", lat)
                parameter("longitude", lon)
                parameter(
                    "hourly",
                    "temperature_2m,relative_humidity_2m,precipitation_probability," +
                        "precipitation,weather_code,cloud_cover,pressure_msl," +
                        "wind_speed_10m,wind_direction_10m,uv_index,is_day",
                )
                parameter("wind_speed_unit", "ms")
                parameter("timezone", "UTC")
                parameter("forecast_days", 14)
            }
        }.body()
    }
}
