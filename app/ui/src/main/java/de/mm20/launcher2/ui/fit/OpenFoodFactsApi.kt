package de.mm20.launcher2.ui.fit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder

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

    private fun getGreekFallback(word: String): String? {
        val lower = word.lowercase(java.util.Locale.ROOT)
        return when {
            lower.contains("μήλο") || lower.contains("μηλο") -> "apple"
            lower.contains("γάλα") || lower.contains("γαλα") -> "milk"
            lower.contains("κοτόπουλο") || lower.contains("κοτοπουλο") -> "chicken"
            lower.contains("ψωμί") || lower.contains("ψωμι") -> "bread"
            lower.contains("γιαούρτι") || lower.contains("γιαουρτι") -> "yogurt"
            lower.contains("τυρί") || lower.contains("τυρι") -> "cheese"
            lower.contains("αυγό") || lower.contains("αυγο") -> "egg"
            lower.contains("σαλάτα") || lower.contains("σαλατα") -> "salad"
            lower.contains("καφές") || lower.contains("καφες") -> "coffee"
            lower.contains("ρύζι") || lower.contains("ρυζι") -> "rice"
            lower.contains("μακαρόνια") || lower.contains("μακαρονια") -> "pasta"
            lower.contains("κρέας") || lower.contains("κρεας") -> "meat"
            lower.contains("ψάρι") || lower.contains("ψαρι") -> "fish"
            lower.contains("μέλι") || lower.contains("μελι") -> "honey"
            lower.contains("πατάτα") || lower.contains("πατατα") -> "potato"
            else -> null
        }
    }

    suspend fun search(query: String): List<FoodProduct> = withContext(Dispatchers.IO) {
        try {
            val trimmed = query.trim()
            if (trimmed.length == 0) return@withContext emptyList()

            val primaryResults = queryApi(trimmed, useGreekDomain = true)
            if (primaryResults.size > 0) {
                return@withContext primaryResults
            }

            // Fallback for Greek food terms translated to English
            val fallbackQuery = getGreekFallback(trimmed)
            if (fallbackQuery != null) {
                return@withContext queryApi(fallbackQuery, useGreekDomain = false)
            }

            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun queryApi(query: String, useGreekDomain: Boolean): List<FoodProduct> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val domain = if (useGreekDomain) "el.openfoodfacts.org" else "world.openfoodfacts.org"
        val url = URL("https://$domain/cgi/search.pl?search_terms=$encoded&search_simple=1&action=process&json=1&lc=el&cc=gr")
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "TelosLauncher - Android Launcher")
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        val response = json.decodeFromString<SearchResponse>(text)
        return response.products
    }
}
