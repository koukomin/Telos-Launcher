package de.mm20.launcher2.data.comms.radio

import de.mm20.launcher2.comms.model.RadioStation
import io.ktor.client.*
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class RadioBrowserStation(
    val stationuuid: String,
    val name: String = "",
    val url_resolved: String = "",
    val url: String = "",
    val favicon: String? = ""
)

class RadioBrowserClient(private val httpClient: HttpClient) {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    // radio-browser.info runs several mirrors: the names behind its DNS round robin are looked up,
    // with fixed names as a fallback
    private val fallbackServers = listOf(
        "de1.api.radio-browser.info",
        "de2.api.radio-browser.info",
        "all.api.radio-browser.info",
    )
    private var discovered: List<String>? = null

    private suspend fun servers(): List<String> {
        val cached = discovered
        if (cached != null) return cached
        val found = withContext(Dispatchers.IO) {
            runCatching {
                java.net.InetAddress.getAllByName("all.api.radio-browser.info")
                    .mapNotNull { it.canonicalHostName?.takeIf { name -> name.endsWith("radio-browser.info") } }
                    .distinct()
                    .shuffled()
            }.getOrDefault(emptyList())
        }
        val list = (found + fallbackServers).distinct()
        if (found.isNotEmpty()) discovered = list
        return list
    }

    /** Throws when no server could be reached, so that the screen can tell the reason. */
    suspend fun searchStations(query: String): List<RadioStation> {
        val errors = ArrayList<String>()
        for (server in servers()) {
            try {
                val text = httpClient.get("https://$server/json/stations/search") {
                    header("User-Agent", "Telos Radio")
                    timeout {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 6_000
                    }
                    url {
                        parameters.append("name", query)
                        parameters.append("limit", "50")
                        parameters.append("hidebroken", "true")
                        parameters.append("order", "clickcount")
                        parameters.append("reverse", "true")
                    }
                }.bodyAsText()

                // Decoded here with the generated serializer: asking Ktor for a List<...> failed on some
                // devices with "Serializer for class 'List' is not found"
                val response: List<RadioBrowserStation> = json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(RadioBrowserStation.serializer()), text
                )

                return response
                    .filter { it.url_resolved.isNotBlank() || it.url.isNotBlank() }
                    .map {
                        RadioStation(
                            id = it.stationuuid,
                            name = it.name.trim(),
                            streamUrl = it.url_resolved.ifBlank { it.url },
                            faviconUrl = it.favicon.orEmpty()
                        )
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += server.substringBefore('.') + ": " + (e.message ?: e.javaClass.simpleName).take(60)
            }
        }
        throw java.io.IOException(errors.joinToString("; ").ifBlank { "no server reachable" })
    }

    /** Counts a play for the station, as radio-browser.info asks apps to do */
    suspend fun countClick(stationUuid: String) {
        for (server in servers()) {
            try {
                httpClient.get("https://$server/json/url/$stationUuid") { header("User-Agent", "Telos Radio") }
                return
            } catch (e: Exception) {
                // try the next mirror
            }
        }
    }
}
