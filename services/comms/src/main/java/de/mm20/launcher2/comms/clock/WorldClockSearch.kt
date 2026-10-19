package de.mm20.launcher2.comms.clock

import android.icu.text.TimeZoneNames
import android.icu.util.TimeZone
import java.text.Normalizer
import java.time.ZoneId
import java.util.Locale

data class WorldClockCity(val zoneId: String, val city: String, val country: String, val searchText: String)

object WorldClockSearch {
    private val combiningMarks = Regex("\\p{M}+")
    private val greek = mapOf('α' to "a", 'β' to "v", 'γ' to "g", 'δ' to "d", 'ε' to "e", 'ζ' to "z", 'η' to "i", 'θ' to "th", 'ι' to "i", 'κ' to "k", 'λ' to "l", 'μ' to "m", 'ν' to "n", 'ξ' to "x", 'ο' to "o", 'π' to "p", 'ρ' to "r", 'σ' to "s", 'ς' to "s", 'τ' to "t", 'υ' to "y", 'φ' to "f", 'χ' to "ch", 'ψ' to "ps", 'ω' to "o")
    private var cachedKey: List<String>? = null
    private var cachedIndex: List<WorldClockCity> = emptyList()

    /** ICU supplies translated exemplar cities and zone→country data, without a remote search. */
    @Synchronized
    fun index(locale: Locale, packagedLocaleTags: List<String> = emptyList()): List<WorldClockCity> {
        val locales = (listOf(locale, Locale.ENGLISH, Locale.forLanguageTag("el")) + packagedLocaleTags.map {
            Locale.forLanguageTag(it.replace("-r", "-").replace('_', '-'))
        }).filter { it.language.isNotBlank() && it.language != "und" }.distinctBy { it.toLanguageTag() }
        val key = locales.map { it.toLanguageTag() }
        if (cachedKey == key) return cachedIndex
        val names = locales.map { TimeZoneNames.getInstance(it) }
        val countryNames = mutableMapOf<String, List<String>>()
        val result = ZoneId.getAvailableZoneIds().sorted().mapNotNull { id ->
            if (!id.contains('/')) return@mapNotNull null
            val region = runCatching { TimeZone.getRegion(id) }.getOrDefault("001")
            val city = names.first().getExemplarLocationName(id) ?: id.substringAfterLast('/').replace('_', ' ')
            val countries = countryNames.getOrPut(region) {
                if (region == "001") List(locales.size) { "" } else {
                    val countryLocale = Locale.Builder().setRegion(region).build()
                    locales.map { countryLocale.getDisplayCountry(it) }
                }
            }
            val cities = names.mapNotNull { it.getExemplarLocationName(id) }
            WorldClockCity(id, city, countries.first(), normalize((cities + countries + id.replace('_', ' ')).distinct().joinToString(" ")))
        }
        cachedKey = key
        cachedIndex = result
        return result
    }
    fun search(cities: List<WorldClockCity>, query: String): List<WorldClockCity> {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        return if (tokens.isEmpty()) emptyList() else cities.filter { city -> tokens.all { it in city.searchText } }
    }
    fun matches(city: WorldClockCity, query: String): Boolean = normalize(query).split(' ').filter { it.isNotEmpty() }.all { it in city.searchText }
    fun normalize(value: String): String {
        val plain = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(combiningMarks, "")

        return buildString(plain.length) { plain.forEach { append(greek[it] ?: it) } }.replace('y', 'i').replace('v', 'b').replace("8", "th").replace('3', 'x')
    }
}
