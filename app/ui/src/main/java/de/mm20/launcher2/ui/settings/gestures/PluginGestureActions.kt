package de.mm20.launcher2.ui.settings.gestures

import android.content.Context
import android.net.Uri
import de.mm20.launcher2.plugin.contracts.GestureActionPluginContract
import de.mm20.launcher2.plugin.data.withColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PluginGestureActionOption(
    val authority: String,
    val actionId: String,
    val label: String,
    val pluginLabel: String,
)

/** Queries a single GestureAction plugin for its currently available actions. */
internal suspend fun queryPluginGestureActions(
    context: Context,
    authority: String,
    pluginLabel: String,
): List<PluginGestureActionOption> = withContext(Dispatchers.IO) {
    try {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(authority)
            .path(GestureActionPluginContract.Paths.Actions)
            .build()
        val cursor = context.contentResolver.query(uri, null, null, null)
            ?: return@withContext emptyList()
        cursor.use { c ->
            val results = mutableListOf<PluginGestureActionOption>()
            c.withColumns(GestureActionPluginContract.ActionColumns) {
                while (c.moveToNext()) {
                    val id = c[GestureActionPluginContract.ActionColumns.Id] ?: continue
                    val label = c[GestureActionPluginContract.ActionColumns.Label] ?: continue
                    results += PluginGestureActionOption(authority, id, label, pluginLabel)
                }
            }
            results
        }
    } catch (e: Exception) {
        emptyList()
    }
}
