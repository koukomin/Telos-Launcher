package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import de.mm20.launcher2.database.entities.WidgetEntity
import de.mm20.launcher2.ktx.decodeFromStringOrNull
import kotlinx.serialization.json.Json
import java.util.UUID

sealed class Widget {

    abstract val id: UUID
    abstract val stackId: UUID?
    internal fun toDatabaseEntity(position: Int, parentId: UUID? = null): WidgetEntity {
        return toDatabaseEntity().let {
            WidgetEntity(
                id = it.id,
                type = it.type,
                config = it.config,
                position = position,
                parentId = parentId,
                stackId = it.stackId,
            )
        }
    }

    abstract fun getLabel(context: Context): String

    abstract fun toDatabaseEntity(): PartialWidgetEntity

    companion object {
        fun fromDatabaseEntity(entity: WidgetEntity): Widget? {
            return when (entity.type) {
                WeatherWidget.Type -> {
                    val config: WeatherWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: WeatherWidgetConfig()
                    WeatherWidget(entity.id, entity.stackId, config)
                }
                MusicWidget.Type -> MusicWidget(
                    entity.id,
                    entity.stackId,
                    Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                        ?: MusicWidgetConfig(),
                )
                CalendarWidget.Type -> {
                    val config: CalendarWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: CalendarWidgetConfig()
                    CalendarWidget(entity.id, entity.stackId, config)
                }
                AppsWidget.Type -> {
                    val config: FavoritesWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: FavoritesWidgetConfig()
                    AppsWidget(entity.id, entity.stackId, config)
                }
                AppWidget.Type -> {
                    val config: AppWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: return null
                    AppWidget(
                        entity.id,
                        entity.stackId,
                        config,
                    )
                }
                NotesWidget.Type -> {
                    val config: NotesWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: NotesWidgetConfig()
                    NotesWidget(entity.id, entity.stackId, config)
                }
                BatteryWidget.Type -> {
                    val config: BatteryWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: BatteryWidgetConfig()
                    BatteryWidget(entity.id, entity.stackId, config)
                }
                NetworkWidget.Type -> {
                    val config: NetworkWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: NetworkWidgetConfig()
                    NetworkWidget(entity.id, entity.stackId, config)
                }
                SystemWidget.Type -> {
                    val config: SystemWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: SystemWidgetConfig()
                    SystemWidget(entity.id, entity.stackId, config)
                }
                RemindersWidget.Type -> {
                    val config: RemindersWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: RemindersWidgetConfig()
                    RemindersWidget(entity.id, entity.stackId, config)
                }
                FreezeWidget.Type -> {
                    val config: FreezeWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: FreezeWidgetConfig()
                    FreezeWidget(entity.id, entity.stackId, config)
                }

                else -> null
            }
        }
    }
}



enum class WidgetType(val value: String) {
    INTERNAL("internal"),
    THIRD_PARTY("3rdparty")
}

fun Widget.withStackId(stackId: UUID?): Widget {
    return when (this) {
        is WeatherWidget -> copy(stackId = stackId)
        is MusicWidget -> copy(stackId = stackId)
        is CalendarWidget -> copy(stackId = stackId)
        is AppsWidget -> copy(stackId = stackId)
        is AppWidget -> copy(stackId = stackId)
        is NotesWidget -> copy(stackId = stackId)
        is BatteryWidget -> copy(stackId = stackId)
        is NetworkWidget -> copy(stackId = stackId)
        is SystemWidget -> copy(stackId = stackId)
        is RemindersWidget -> copy(stackId = stackId)
        is FreezeWidget -> copy(stackId = stackId)
    }
}

/**
 * The user-configured height override for this widget, in dp, or `null` if
 * it should size itself naturally (content-driven height).
 */
val Widget.height: Int?
    get() = when (this) {
        is WeatherWidget -> config.height
        is MusicWidget -> config.height
        is CalendarWidget -> config.height
        is AppsWidget -> config.height
        is AppWidget -> config.height
        is NotesWidget -> config.height
        is BatteryWidget -> config.height
        is NetworkWidget -> config.height
        is SystemWidget -> config.height
        is RemindersWidget -> config.height
        is FreezeWidget -> config.height
    }

fun Widget.withHeight(height: Int?): Widget {
    return when (this) {
        is WeatherWidget -> copy(config = config.copy(height = height))
        is MusicWidget -> copy(config = config.copy(height = height))
        is CalendarWidget -> copy(config = config.copy(height = height))
        is AppsWidget -> copy(config = config.copy(height = height))
        is AppWidget -> copy(config = config.copy(height = height ?: config.height))
        is NotesWidget -> copy(config = config.copy(height = height))
        is BatteryWidget -> copy(config = config.copy(height = height))
        is NetworkWidget -> copy(config = config.copy(height = height))
        is SystemWidget -> copy(config = config.copy(height = height))
        is RemindersWidget -> copy(config = config.copy(height = height))
        is FreezeWidget -> copy(config = config.copy(height = height))
    }
}