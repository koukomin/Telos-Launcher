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

    /** Countries whose channels are shown; empty means "use the suggestion for the locale" */
    val selectedCountries: StateFlow<List<String>> get() = _countries
    /** Optional language filter; empty means no filter */
    val selectedLanguages: StateFlow<List<String>> get() = _languages
    val disclaimerShown: StateFlow<Boolean> get() = _disclaimerShown

    private var bad: Map<String, Long> = emptyMap()

    init {
        val j = readLocked()
        _countries.value = j.optJSONArray("countries").toStrings().map { TvIndex.normalizeCountry(it) }.distinct()
        _languages.value = j.optJSONArray("languages").toStrings().map { it.lowercase() }.distinct()
        _disclaimerShown.value = j.optBoolean("disclaimer", false)
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

    fun restoreJson(j: JSONObject) {
        setSelectedCountries(j.optJSONArray("countries").toStrings())
        setSelectedLanguages(j.optJSONArray("languages").toStrings())
        // the disclaimer is never switched off by a backup, only on
        if (j.optBoolean("disclaimer", false)) setDisclaimerShown(true)
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

    private companion object {
        const val FILE = "tv_prefs.json"
        const val MAX_BAD = 500
    }
}
