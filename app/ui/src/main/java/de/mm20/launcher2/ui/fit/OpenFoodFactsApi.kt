package de.mm20.launcher2.ui.fit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Serializable
data class FoodProduct(
    val code: String? = null,
    val product_name: String? = null,
    val brands: String? = null,
    val nutriments: Nutriments? = null,
)

@Serializable
data class Nutriments(
    @SerialName("energy-kcal_100g")
    val energyKcal100g: Float? = 0f,
    @SerialName("proteins_100g")
    val proteins100g: Float? = 0f,
    @SerialName("carbohydrates_100g")
    val carbohydrates100g: Float? = 0f,
    @SerialName("fat_100g")
    val fat100g: Float? = 0f,
)

@Serializable
data class SearchResponse(
    val products: List<FoodProduct> = emptyList()
)

object OpenFoodFactsApi {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(query: String): List<FoodProduct> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext emptyList()
            val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
            val url = URL(
                "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$encoded&search_simple=1&action=process&json=1",
            )
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "TelosLauncher - Android Launcher")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val response = json.decodeFromString<SearchResponse>(text)
            response.products
        } catch (_: Exception) {
            emptyList()
        }
    }
}
