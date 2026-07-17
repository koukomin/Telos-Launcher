package de.mm20.launcher2.plugin.contracts

/**
 * Contract for [de.mm20.launcher2.plugin.PluginType.Widget] plugins. Deliberately data-only,
 * not UI-hosting: a plugin returns a short list of title/subtitle/value rows (queried via
 * [Paths.Items]), which the launcher renders using its own widget UI. This mirrors the rest of
 * the plugin SDK's design (plugins supply structured data, the launcher renders it), and avoids
 * the security and compatibility problems of trying to host arbitrary third-party UI in-process.
 */
object WidgetPluginContract {
    object Paths {
        /** content://<authority>/items - query for the widget's current content. */
        const val Items = "items"
    }

    object Params {
        const val Language = "lang"
    }

    object ItemColumns : Columns() {
        val Title = column<String>("title")
        val Subtitle = column<String>("subtitle")
        val Value = column<String>("value")
    }
}
