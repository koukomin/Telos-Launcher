package de.mm20.launcher2.comms.clock

import android.icu.text.TimeZoneNames
import android.icu.util.TimeZone
import java.text.Normalizer
import java.time.ZoneId
import java.util.Locale

data class WorldClockCity(val zoneId: String, val city: String, val country: String, val searchText: String)

object WorldClockSearch {
    /** ICU supplies translated exemplar cities and zone→country data, without a remote search. */
    fun index(locale: Locale): List<WorldClockCity> {
        val locales = listOf(locale, Locale.ENGLISH, Locale.forLanguageTag("el")).distinct()
        val names = locales.map { TimeZoneNames.getInstance(it) }
        return ZoneId.getAvailableZoneIds().sorted().mapNotNull { id ->
            if (!id.contains('/')) return@mapNotNull null
            val region = runCatching { TimeZone.getRegion(id) }.getOrDefault("001")
            val countryLocale = Locale.Builder().setRegion(region.takeIf { it != "001" }.orEmpty()).build()
            val city = names.first().getExemplarLocationName(id) ?: id.substringAfterLast('/').replace('_', ' ')
            val countries = locales.map { if (region == "001") "" else countryLocale.getDisplayCountry(it) }
            val cities = names.mapNotNull { it.getExemplarLocationName(id) }
            WorldClockCity(id, city, countries.first(), normalize((cities + countries + id.replace('_', ' ')).joinToString(" ")))
        }
    }
    fun matches(city: WorldClockCity, query: String): Boolean = normalize(query).split(' ').filter { it.isNotEmpty() }.all { it in city.searchText }
    fun normalize(value: String): String {
        val plain = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val greek = mapOf('α' to "a", 'β' to "v", 'γ' to "g", 'δ' to "d", 'ε' to "e", 'ζ' to "z", 'η' to "i", 'θ' to "th", 'ι' to "i", 'κ' to "k", 'λ' to "l", 'μ' to "m", 'ν' to "n", 'ξ' to "x", 'ο' to "o", 'π' to "p", 'ρ' to "r", 'σ' to "s", 'ς' to "s", 'τ' to "t", 'υ' to "y", 'φ' to "f", 'χ' to "ch", 'ψ' to "ps", 'ω' to "o")
        return plain.map { greek[it] ?: it.toString() }.joinToString("").replace('y', 'i').replace('v', 'b').replace("8", "th").replace('3', 'x')
    }
}
