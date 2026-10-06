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

    // radio-browser.info runs several mirrors; try the next one when a server does not answer
    private val servers = listOf(
        "de1.api.radio-browser.info",
        "nl1.api.radio-browser.info",
        "at1.api.radio-browser.info",
    )

    suspend fun searchStations(query: String): List<RadioStation> {
        for (server in servers) {
            try {
                val response: List<RadioBrowserStation> = httpClient.get("https://$server/json/stations/search") {
                    header("User-Agent", "Telos Radio")
                    url {
                        parameters.append("name", query)
                        parameters.append("limit", "50")
                        parameters.append("hidebroken", "true")
                        parameters.append("order", "clickcount")
                        parameters.append("reverse", "true")
                    }
                }.body()

                return response.map {
                    RadioStation(
                        id = it.stationuuid,
                        name = it.name.trim(),
                        streamUrl = it.url_resolved,
                        faviconUrl = it.favicon
                    )
                }
            } catch (e: Exception) {
                // try the next mirror
            }
        }
        return emptyList()
    }

    /** Counts a play for the station, as radio-browser.info asks apps to do */
    suspend fun countClick(stationUuid: String) {
        for (server in servers) {
            try {
                httpClient.get("https://$server/json/url/$stationUuid") { header("User-Agent", "Telos Radio") }
                return
            } catch (e: Exception) {
                // try the next mirror
            }
        }
    }
}
