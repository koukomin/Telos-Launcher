package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class NetworkWidgetConfig(
    val height: Int? = null,
)

data class NetworkWidget(
    override val id: UUID,
    override val stackId: UUID? = null,
    val config: NetworkWidgetConfig = NetworkWidgetConfig(),
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
        return context.getString(R.string.widget_name_network)
    }

    companion object {
        const val Type = "network"
    }
}
