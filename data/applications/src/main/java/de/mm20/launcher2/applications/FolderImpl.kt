package de.mm20.launcher2.applications

import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.Folder
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import org.json.JSONArray
import org.json.JSONObject

data class FolderImpl(
    val id: String,
    override val label: String,
    override val itemKeys: List<String>,
    override val isCover: Boolean = false,
    override val labelOverride: String? = null,
) : Folder {
    override val domain: String = Domain

    override val key: String = "$domain://$id"

    override fun overrideLabel(label: String): Folder {
        return this.copy(labelOverride = label)
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        return null
    }

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val color = 0xFF4285F4.toInt() // Default blue
        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                icon = ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.folder_24px)!!,
                scale = 0.6f,
                color = color,
            ),
            backgroundLayer = ColorLayer(color)
        )
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        return false
    }

    override fun getSerializer(): SearchableSerializer {
        return FolderSerializer()
    }

    companion object {
        const val Domain = "folder"
    }
}

class FolderSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as FolderImpl
        return jsonObjectOf(
            "id" to searchable.id,
            "label" to searchable.label,
            "items" to JSONArray(searchable.itemKeys),
            "cover" to searchable.isCover,
        ).toString()
    }

    override val typePrefix: String = FolderImpl.Domain
}

class FolderDeserializer : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        val json = JSONObject(serialized)
        val items = json.optJSONArray("items")?.let { arr ->
            List(arr.length()) { arr.getString(it) }
        } ?: emptyList()
        return FolderImpl(
            id = json.getString("id"),
            label = json.getString("label"),
            itemKeys = items,
            isCover = json.optBoolean("cover", false),
        )
    }
}
