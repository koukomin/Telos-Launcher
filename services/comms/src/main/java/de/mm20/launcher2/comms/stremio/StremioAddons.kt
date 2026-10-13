package de.mm20.launcher2.comms.stremio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The add-ons the user installed, and the small settings of the Stremio space, in plain files in the app's
 * private storage (filesDir/stremio_addons.json and stremio_prefs.json, written atomically). Telos ships
 * no add-on: the list is empty until the user adds one.
 */
class StremioAddons(context: Context, private val client: StremioClient) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "stremio_addons.json")
    private val prefs = appContext.getSharedPreferences("stremio_prefs", Context.MODE_PRIVATE)
    private val lock = Any()

    private val _addons = MutableStateFlow(load())
    val addons: StateFlow<List<Addon>> get() = _addons

    /** The user accepted the notice about content (what the add-ons offer is not Telos' content) */
    private val _noticeAccepted = MutableStateFlow(prefs.getBoolean("notice", false))
    val noticeAccepted: StateFlow<Boolean> get() = _noticeAccepted

    fun acceptNotice() {
        prefs.edit().putBoolean("notice", true).apply()
        _noticeAccepted.value = true
    }

    private fun load(): List<Addon> = synchronized(lock) {
        runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val raw = o.optString("manifest")
                val manifest = StremioProtocol.parseManifest(raw) ?: return@mapNotNull null
                Addon(o.optString("url"), manifest, raw, o.optBoolean("official"), o.optBoolean("protected")).takeIf { it.baseUrl.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<Addon>) = synchronized(lock) {
        runCatching {
            val arr = JSONArray()
            // the manifest is stored as the add-on sent it, in a compact form of what was parsed
            list.forEach {
                arr.put(
                    JSONObject().put("url", it.baseUrl).put("manifest", it.rawManifest.ifBlank { manifestJson(it.manifest) })
                        .put("official", it.official).put("protected", it.protected)
                )
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
        Unit
    }

    /** Installs the add-on at [input] (an address, `stremio://` link or manifest address); an add-on already installed is updated */
    suspend fun add(input: String): Result<Addon> {
        val result = client.fetchAddon(input)
        result.onSuccess { addon ->
            val next = _addons.value.filter { it.baseUrl != addon.baseUrl && it.manifest.id != addon.manifest.id } + addon
            _addons.value = next
            save(next)
        }
        return result
    }

    /** Installs add-ons that came from somewhere else (a directory, the add-ons of a Stremio account); existing ones are kept */
    fun upsert(list: List<Addon>) {
        val current = _addons.value
        val next = current + list.filter { n -> current.none { it.baseUrl == n.baseUrl || it.manifest.id == n.manifest.id } }
        if (next.size != current.size) {
            _addons.value = next
            save(next)
        }
    }

    fun install(d: DirectoryAddon) = upsert(listOf(Addon(d.baseUrl, d.manifest, d.rawManifest)))

    fun remove(baseUrl: String) {
        val next = _addons.value.filter { it.baseUrl != baseUrl }
        _addons.value = next
        save(next)
    }

    /** Asks every add-on for its manifest again (a new version may have new catalogs) */
    suspend fun refresh() {
        val updated = _addons.value.map { a -> client.fetchAddon(a.baseUrl).getOrNull() ?: a }
        _addons.value = updated
        save(updated)
    }

    companion object {
        /** Stremio's own metadata add-on, offered as a one-tap suggestion (never installed without the user's tap) */
        const val CINEMETA = "https://v3-cinemeta.strem.io"

        /** The public list of community add-ons that Stremio's apps show; may change or be gone, then the directory shows less */
        const val PUBLIC_LIST = "https://api.strem.io/addonscollection.json"

        private fun manifestJson(m: AddonManifest): String {
            val o = JSONObject()
                .put("id", m.id).put("name", m.name).put("description", m.description).put("version", m.version)
                .put("types", JSONArray(m.types)).put("idPrefixes", JSONArray(m.idPrefixes))
            m.logo?.let { o.put("logo", it) }
            o.put("catalogs", JSONArray().also { arr ->
                m.catalogs.forEach { c ->
                    arr.put(JSONObject().put("type", c.type).put("id", c.id).put("name", c.name).put("extra", JSONArray().also { ea ->
                        c.extras.forEach { e ->
                            ea.put(JSONObject().put("name", e.name).put("isRequired", e.isRequired).put("options", JSONArray(e.options)))
                        }
                    }))
                }
            })
            o.put("resources", JSONArray().also { arr ->
                m.resources.forEach { r ->
                    arr.put(JSONObject().put("name", r.name).put("types", JSONArray(r.types)).put("idPrefixes", JSONArray(r.idPrefixes)))
                }
            })
            o.put("behaviorHints", JSONObject().put("p2p", m.p2p).put("adult", m.adult).put("configurationRequired", m.configurationRequired))
            return o.toString()
        }
    }
}
