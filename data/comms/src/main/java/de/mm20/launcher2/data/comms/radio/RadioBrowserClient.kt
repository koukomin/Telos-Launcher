package de.mm20.launcher2.data.comms.radio

import de.mm20.launcher2.comms.model.RadioStation
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class RadioBrowserStation(
    val stationuuid: String,
    val name: String,
    val url_resolved: String,
    val favicon: String
)

class RadioBrowserClient(private val httpClient: HttpClient) {

    suspend fun searchStations(query: String): List<RadioStation> {
        return try {
            val response: List<RadioBrowserStation> = httpClient.get("https://de1.api.radio-browser.info/json/stations/search") {
                url {
                    parameters.append("name", query)
                    parameters.append("limit", "50")
                }
            }.body()

            response.map {
                RadioStation(
                    id = it.stationuuid,
                    name = it.name.trim(),
                    streamUrl = it.url_resolved,
                    faviconUrl = it.favicon
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
