package de.mm20.launcher2.comms.tv

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Small settings of Telos TV in a plain file (filesDir/tv_prefs.json, written atomically): the selected
 * countries and languages, the "disclaimer shown" flag and the map of streams that failed recently.
 * Thread safe. Country codes are ISO 3166 (upper case), language codes ISO 639-3 (lower case).
 */
class TvSettings(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE)
    private val lock = Any()

    private val _countries = MutableStateFlow<List<String>>(emptyList())
    private val _languages = MutableStateFlow<List<String>>(emptyList())
    private val _disclaimerShown = MutableStateFlow(false)
    private val _extraPlaylists = MutableStateFlow(true)
    private val _epg = MutableStateFlow(true)

    /** Countries whose channels are shown; empty means "use the suggestion for the locale" */
    val selectedCountries: StateFlow<List<String>> get() = _countries
    /** Optional language filter; empty means no filter */
    val selectedLanguages: StateFlow<List<String>> get() = _languages
    val disclaimerShown: StateFlow<Boolean> get() = _disclaimerShown
    /** Optional extra Greek playlists (Free-TV, greektvm3u); default on, only active when Greece is selected (see [TvExtraSources]) */
    val extraPlaylistsEnabled: StateFlow<Boolean> get() = _extraPlaylists
    /** Optional programme guide (EPG); default on, only active when Greece is selected */
    val epgEnabled: StateFlow<Boolean> get() = _epg

    private var bad: Map<String, Long> = emptyMap()

    init {
        val j = readLocked()
        _countries.value = j.optJSONArray("countries").toStrings().map { TvIndex.normalizeCountry(it) }.distinct()
        _languages.value = j.optJSONArray("languages").toStrings().map { it.lowercase() }.distinct()
        _disclaimerShown.value = j.optBoolean("disclaimer", false)
        _extraPlaylists.value = j.optBoolean("extraPlaylists", true)
        _epg.value = j.optBoolean("epg", true)
        val b = j.optJSONObject("bad")
        bad = buildMap {
            if (b != null) {
                val keys = b.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, b.optLong(k))
                }
            }
        }
    }

    fun setSelectedCountries(codes: List<String>) {
        val clean = codes.map { TvIndex.normalizeCountry(it) }.filter { it.length == 2 }.distinct().take(300)
        _countries.value = clean
        save()
    }

    fun setSelectedLanguages(codes: List<String>) {
        val clean = codes.map { it.trim().lowercase() }.filter { it.length in 2..3 }.distinct().take(100)
        _languages.value = clean
        save()
    }

    fun setExtraPlaylistsEnabled(enabled: Boolean) {
        _extraPlaylists.value = enabled
        save()
    }

    fun setEpgEnabled(enabled: Boolean) {
        _epg.value = enabled
        save()
    }

    /**
     * Home country resolved by the UI (may be blank). Only a hint for [greeceSelected] on a fresh install,
     * where no country is selected yet.
     */
    @Volatile var homeCountryHint: String = ""

    /**
     * True when the Greek extra sources and guide apply: Greece is selected, or nothing is selected yet
     * (first run) and the home country, the locale country or the language is Greek.
     */
    fun greeceSelected(): Boolean = greeceActive(_countries.value, homeCountryHint, java.util.Locale.getDefault())

    fun setDisclaimerShown(shown: Boolean) {
        _disclaimerShown.value = shown
        save()
    }

    /** URLs of streams that failed within the last 24 hours, with the time of the failure */
    fun badStreams(now: Long = System.currentTimeMillis()): Map<String, Long> = synchronized(lock) {
        TvFailover.prune(bad, now)
    }

    fun markBad(url: String, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            bad = (TvFailover.prune(bad, now) + (url to now)).entries
                .sortedByDescending { it.value }.take(MAX_BAD).associate { it.key to it.value }
        }
        save()
    }

    fun clearBad(url: String) {
        val changed = synchronized(lock) {
            if (url in bad) { bad = bad - url; true } else false
        }
        if (changed) save()
    }

    /** The part that belongs into a backup (not the failure map) */
    fun exportJson(): JSONObject = JSONObject()
        .put("countries", JSONArray(_countries.value))
        .put("languages", JSONArray(_languages.value))
        .put("disclaimer", _disclaimerShown.value)
        .put("extraPlaylists", _extraPlaylists.value)
        .put("epg", _epg.value)

    fun restoreJson(j: JSONObject) {
        setSelectedCountries(j.optJSONArray("countries").toStrings())
        setSelectedLanguages(j.optJSONArray("languages").toStrings())
        // the disclaimer is never switched off by a backup, only on
        if (j.optBoolean("disclaimer", false)) setDisclaimerShown(true)
        // older backups have no such keys: keep the current value
        if (j.has("extraPlaylists")) setExtraPlaylistsEnabled(j.optBoolean("extraPlaylists", true))
        if (j.has("epg")) setEpgEnabled(j.optBoolean("epg", true))
    }

    private fun readLocked(): JSONObject = synchronized(lock) {
        runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    }

    private fun save() {
        synchronized(lock) {
            runCatching {
                val j = JSONObject()
                    .put("countries", JSONArray(_countries.value))
                    .put("languages", JSONArray(_languages.value))
                    .put("disclaimer", _disclaimerShown.value)
                    .put("extraPlaylists", _extraPlaylists.value)
                    .put("epg", _epg.value)
                val b = JSONObject()
                for ((k, v) in bad) b.put(k, v)
                j.put("bad", b)
                val tmp = File(file.parentFile, "$FILE.tmp")
                tmp.writeText(j.toString())
                tmp.renameTo(file)
            }
        }
    }

    private fun JSONArray?.toStrings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

    companion object {
        /** Pure rule behind [greeceSelected] */
        fun greeceActive(selected: List<String>, home: String, locale: java.util.Locale): Boolean {
            if (selected.isNotEmpty()) return "GR" in selected
            return home.trim().equals("GR", ignoreCase = true) ||
                locale.country.equals("GR", ignoreCase = true) ||
                locale.language.equals("el", ignoreCase = true)
        }

        /** The country to preselect on first run: the home country, else the locale country, else GR for Greek; blank if unknown */
        fun defaultCountry(home: String, locale: java.util.Locale): String = when {
            home.isNotBlank() -> TvIndex.normalizeCountry(home)
            locale.country.length == 2 -> TvIndex.normalizeCountry(locale.country)
            locale.language.equals("el", ignoreCase = true) -> "GR"
            else -> ""
        }

        private const val FILE = "tv_prefs.json"
        private const val MAX_BAD = 500
    }
}
