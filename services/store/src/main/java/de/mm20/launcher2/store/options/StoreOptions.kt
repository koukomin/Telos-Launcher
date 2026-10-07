package de.mm20.launcher2.store.options

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Settings of one tracked app (what Obtainium calls the additional settings of an app). */
@Serializable
data class ItemOptions(
    /** Only tell that a new version exists, never download or install it */
    val trackOnly: Boolean = false,
    /** Stay on the installed version: no update is offered */
    val pinned: Boolean = false,
    /** A version the user chose to ignore */
    val skippedVersion: String? = null,
    /** Not checked and not updated by the background check */
    val excludeFromBackground: Boolean = false,
    /** A label to group apps */
    val category: String = "",
    val note: String = "",
)

/** Settings of the Store as a whole. */
@Serializable
data class GlobalOptions(
    /** Hours between background checks, 0 switches the background check off */
    val checkIntervalHours: Int = 6,
    val wifiOnly: Boolean = true,
    val notifyUpdates: Boolean = true,
    /** Install updates without asking when Shizuku or root allows it (never otherwise) */
    val autoInstall: Boolean = false,
    /** Raises the GitHub limit from 60 to 5000 requests per hour; optional */
    val githubToken: String = "",
    val lastCheckMillis: Long = 0,
)

/**
 * The per-app and global settings of the Store. Kept in the app's private storage (not in the
 * database), so adding a setting never needs a database migration.
 */
class StoreOptions(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("store_options", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _items = MutableStateFlow(loadItems())
    val items: StateFlow<Map<String, ItemOptions>> = _items

    private val _global = MutableStateFlow(loadGlobal())
    val global: StateFlow<GlobalOptions> = _global

    private fun loadItems(): Map<String, ItemOptions> {
        val map = HashMap<String, ItemOptions>()
        for ((key, value) in prefs.all) {
            if (!key.startsWith("item:") || value !is String) continue
            runCatching { json.decodeFromString(ItemOptions.serializer(), value) }
                .onSuccess { map[key.removePrefix("item:")] = it }
        }
        return map
    }

    private fun loadGlobal(): GlobalOptions =
        runCatching { json.decodeFromString(GlobalOptions.serializer(), prefs.getString("global", "") ?: "") }
            .getOrDefault(GlobalOptions())

    fun item(id: String): ItemOptions = _items.value[id] ?: ItemOptions()

    @Synchronized
    fun update(id: String, change: (ItemOptions) -> ItemOptions) {
        val next = change(item(id))
        prefs.edit().putString("item:$id", json.encodeToString(ItemOptions.serializer(), next)).apply()
        _items.value = _items.value + (id to next)
    }

    @Synchronized
    fun remove(id: String) {
        prefs.edit().remove("item:$id").apply()
        _items.value = _items.value - id
    }

    @Synchronized
    fun updateGlobal(change: (GlobalOptions) -> GlobalOptions) {
        val next = change(_global.value)
        prefs.edit().putString("global", json.encodeToString(GlobalOptions.serializer(), next)).apply()
        _global.value = next
    }
}
