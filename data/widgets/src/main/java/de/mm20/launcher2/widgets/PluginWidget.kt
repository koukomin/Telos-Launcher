package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class PluginWidgetConfig(
    val authority: String = "",
    /** Cached label of the plugin, shown while the widget content is loading. */
    val label: String? = null,
    val height: Int? = null,
)

data class PluginWidget(
    override val id: UUID,
    override val stackId: UUID? = null,
    val config: PluginWidgetConfig = PluginWidgetConfig(),
) : Widget() {
    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
            stackId = stackId,
        )
    }

    override fun getLabel(context: Context): String {
        return config.label ?: config.authority
    }

    companion object {
        const val Type = "plugin"
    }
}
