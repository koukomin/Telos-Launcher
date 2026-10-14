package de.mm20.launcher2.ui.files

import android.content.Context

enum class FilesToolbarAction { View, Sort, Hidden, Refresh }

data class FilesToolbarConfig(
    val order: List<FilesToolbarAction> = FilesToolbarAction.entries.toList(),
    val visible: Set<FilesToolbarAction> = setOf(FilesToolbarAction.View),
) {
    val actions: List<FilesToolbarAction> get() = order.filter { it in visible }.take(MAX_ACTIONS)

    companion object {
        const val MAX_ACTIONS = 2
        fun decode(order: String?, visible: String?): FilesToolbarConfig {
            fun parse(text: String?) = text.orEmpty().split(',').mapNotNull { name ->
                FilesToolbarAction.entries.firstOrNull { it.name == name }
            }.distinct()
            val sorted = parse(order).let { it + FilesToolbarAction.entries.filterNot(it::contains) }
            val enabled = if (visible == null) setOf(FilesToolbarAction.View) else parse(visible).toSet()
            return FilesToolbarConfig(sorted, sorted.filter { it in enabled }.take(MAX_ACTIONS).toSet())
        }
    }
}

object FilesToolbarPreferences {
    fun read(context: Context): FilesToolbarConfig {
        val prefs = context.getSharedPreferences("telos_files", Context.MODE_PRIVATE)
        return FilesToolbarConfig.decode(prefs.getString("toolbarOrder", null), prefs.getString("toolbarVisible", null))
    }

    fun write(context: Context, config: FilesToolbarConfig) {
        val safe = FilesToolbarConfig.decode(config.order.joinToString(",") { it.name }, config.visible.joinToString(",") { it.name })
        context.getSharedPreferences("telos_files", Context.MODE_PRIVATE).edit()
            .putString("toolbarOrder", safe.order.joinToString(",") { it.name })
            .putString("toolbarVisible", safe.actions.joinToString(",") { it.name }).apply()
    }
}
