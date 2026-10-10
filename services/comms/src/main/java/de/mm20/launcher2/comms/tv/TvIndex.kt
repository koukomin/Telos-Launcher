package de.mm20.launcher2.comms.tv

import de.mm20.launcher2.comms.search.TelosSearch
import java.util.Locale

/**
 * The compact in-memory index of the catalog: only channels that are playable and allowed (not NSFW,
 * not closed or replaced, not blocklisted, with at least one stream). Immutable, safe to use from any thread.
 * Searching walks all channels (roughly 10 thousand), so call [search] off the main thread.
 */
class TvIndex(channels: List<TvChannel>, internal val categoryNames: Map<String, String> = emptyMap()) {

    /** All channels sorted by name */
    val all: List<TvChannel> = channels.sortedBy { it.name.lowercase() }

    private val byId: Map<String, TvChannel> = all.associateBy { it.id }
    private val countryMap: Map<String, List<TvChannel>> = all.filter { it.country.isNotEmpty() }.groupBy { it.country }
    private val languageMap: Map<String, List<TvChannel>> =
        HashMap<String, MutableList<TvChannel>>().also { m ->
            for (c in all) for (l in c.languages) m.getOrPut(l) { ArrayList() }.add(c)
        }
    private val categoryMap: Map<String, List<TvChannel>> =
        HashMap<String, MutableList<TvChannel>>().also { m ->
            for (c in all) for (k in c.categories) m.getOrPut(k) { ArrayList() }.add(c)
        }

    val size: Int get() = all.size

    fun channel(id: String): TvChannel? = byId[id]

    /** Channels of any of the countries (ISO 3166 codes, case insensitive; "UK" is accepted for "GB"), sorted by name */
    fun byCountry(codes: Collection<String>): List<TvChannel> =
        codes.map { normalizeCountry(it) }.distinct().flatMap { countryMap[it].orEmpty() }.sortedBy { it.name.lowercase() }

    /** Channels in any of the languages (ISO 639-3 codes such as "ell", or 2 letter codes such as "el") */
    fun byLanguage(codes: Collection<String>): List<TvChannel> =
        codes.mapNotNull { language3(it) }.distinct().flatMap { languageMap[it].orEmpty() }
            .distinctBy { it.id }.sortedBy { it.name.lowercase() }

    /** Channels of the category (catalog id such as "news", "sports", "kids") */
    fun byCategory(id: String): List<TvChannel> = categoryMap[id.lowercase()].orEmpty()

    /**
     * Matches names and alternative names with [TelosSearch] (accent insensitive, Greek and Greeklish),
     * best matches first, at most [limit]. A blank query returns an empty list.
     */
    fun search(query: String, limit: Int = 100): List<TvChannel> {
        if (query.isBlank()) return emptyList()
        return TelosSearch.filter(all, query) { listOf(it.name) + it.altNames }.take(limit)
    }

    /**
     * What to show first: channels of the home country, then channels in the language of the app from
     * other countries. [homeCountry] is an ISO 3166 code (may be blank), [appLanguage] a 2 or 3 letter language code.
     */
    fun suggestedForLocale(homeCountry: String, appLanguage: String, limit: Int = 60): List<TvChannel> {
        val home = if (homeCountry.isBlank()) emptyList() else byCountry(listOf(homeCountry))
        val lang = language3(appLanguage)?.let { languageMap[it].orEmpty() }.orEmpty()
        return (home + lang.sortedBy { it.name.lowercase() }).distinctBy { it.id }.take(limit)
    }

    /** Countries that have channels, names in [locale], sorted by name */
    fun countries(locale: Locale = Locale.getDefault()): List<TvCountry> =
        countryMap.map { (code, list) -> TvCountry(code, countryName(code, locale), flagEmoji(code), list.size) }
            .sortedBy { it.name.lowercase(locale) }

    /** Categories that have channels, sorted by name */
    fun categories(): List<TvCategory> =
        categoryMap.map { (id, list) -> TvCategory(id, categoryNames[id] ?: prettify(id), list.size) }
            .sortedBy { it.name.lowercase() }

    /** Languages that have channels, names in [locale], sorted by name */
    fun languages(locale: Locale = Locale.getDefault()): List<TvLanguage> =
        languageMap.map { (code, list) -> TvLanguage(code, languageName(code, locale), list.size) }
            .sortedBy { it.name.lowercase(locale) }

    companion object {
        /** The catalog writes "UK" for the United Kingdom, Java and Android use "GB" */
        fun normalizeCountry(code: String): String {
            val c = code.trim().uppercase()
            return if (c == "UK") "GB" else c
        }

        /** Two letter regional indicator symbols ("GR" becomes the Greek flag); empty for anything else */
        fun flagEmoji(code: String): String {
            val c = normalizeCountry(code)
            if (c.length != 2 || c.any { it !in 'A'..'Z' }) return ""
            val base = 0x1F1E6 - 'A'.code
            return String(Character.toChars(base + c[0].code)) + String(Character.toChars(base + c[1].code))
        }

        fun countryName(code: String, locale: Locale = Locale.getDefault()): String =
            Locale("", normalizeCountry(code)).getDisplayCountry(locale).ifBlank { code }

        fun languageName(code3: String, locale: Locale = Locale.getDefault()): String =
            Locale(code3).getDisplayLanguage(locale).ifBlank { code3 }

        /** "el" or "ell" or "gre" to "ell"; null when unknown */
        fun language3(code: String): String? {
            val c = code.trim().lowercase()
            return when (c.length) {
                2 -> runCatching { Locale(c).isO3Language }.getOrNull()?.takeIf { it.length == 3 }
                3 -> c
                else -> null
            }
        }

        private fun prettify(id: String) = id.replace('-', ' ').replaceFirstChar { it.uppercase() }
    }
}
