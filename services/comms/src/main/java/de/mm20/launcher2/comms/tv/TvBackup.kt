package de.mm20.launcher2.comms.tv

import org.json.JSONObject

/**
 * One JSON document with everything the user has in TV: the data of [TvRepository] and the
 * selections of [TvSettings] (not the failure map and not the catalog cache). Same idea as the
 * radio collection backup.
 */
class TvBackup(private val repository: TvRepository, private val settings: TvSettings) {

    suspend fun export(): String = JSONObject()
        .put("version", 1)
        .put("settings", settings.exportJson())
        .put("data", JSONObject(repository.exportBackup()))
        .toString(2)

    /** Returns the number of restored items (favorites, channels, ...); 0 for unusable input. Never throws. */
    suspend fun restore(json: String): Int {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return 0
        root.optJSONObject("settings")?.let { runCatching { settings.restoreJson(it) } }
        val data = root.optJSONObject("data") ?: return 0
        return runCatching { repository.restoreBackup(data.toString()) }.getOrDefault(0)
    }
}
