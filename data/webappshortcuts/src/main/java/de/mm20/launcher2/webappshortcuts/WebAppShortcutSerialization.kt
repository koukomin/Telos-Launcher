package de.mm20.launcher2.webappshortcuts

import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import org.json.JSONObject
import java.util.UUID

class WebAppShortcutSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as WebAppShortcutImpl
        return jsonObjectOf(
            "id" to searchable.id,
            "label" to searchable.label,
            "url" to searchable.url,
            "iconUri" to searchable.iconUri,
            "favicon" to searchable.faviconUrl,
            "color" to searchable.color
        ).toString()
    }

    override val typePrefix: String
        get() = "webappshortcut"
}

class WebAppShortcutDeserializer : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        val json = JSONObject(serialized)
        return WebAppShortcutImpl(
            id = json.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            label = json.getString("label"),
            url = json.getString("url"),
            iconUri = json.optString("iconUri").takeIf { it.isNotBlank() },
            faviconUrl = json.optString("favicon").takeIf { it.isNotBlank() },
            color = json.optInt("color").takeIf { it != 0 },
        )
    }
}
