package de.mm20.launcher2.sdk.widget

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.contracts.WidgetPluginContract
import de.mm20.launcher2.plugin.contracts.WidgetPluginContract.ItemColumns
import de.mm20.launcher2.plugin.data.buildCursor
import de.mm20.launcher2.sdk.base.BasePluginProvider
import de.mm20.launcher2.sdk.utils.launchWithCancellationSignal
import java.util.Locale

/**
 * A single row of widget content.
 * @param title The main text of this row.
 * @param subtitle Optional secondary text.
 * @param value Optional value text, shown aligned to the end of the row (e.g. "42%", "3 new").
 */
data class PluginWidgetItem(
    val title: String,
    val subtitle: String? = null,
    val value: String? = null,
)

/**
 * Base class for [PluginType.Widget] plugins: exposes a short, read-only list of content rows
 * that the launcher renders as a home screen widget. This is deliberately data-only - the
 * launcher owns the widget's visual presentation, the plugin only supplies content.
 */
abstract class WidgetProvider : BasePluginProvider() {
    override fun getPluginType(): PluginType {
        return PluginType.Widget
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return query(uri, projection, null, null)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?
    ): Cursor? {
        val context = context ?: return null
        checkPermissionOrThrow(context)

        if (uri.pathSegments.size == 1 && uri.pathSegments.first() == WidgetPluginContract.Paths.Items) {
            val lang = uri.getQueryParameter(WidgetPluginContract.Params.Language)
                ?: Locale.getDefault().language
            val items = launchWithCancellationSignal(cancellationSignal) {
                getWidgetItems(lang)
            }
            return buildCursor(ItemColumns, items) {
                put(ItemColumns.Title, it.title)
                put(ItemColumns.Subtitle, it.subtitle)
                put(ItemColumns.Value, it.value)
            }
        }
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("This operation is not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("This operation is not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("This operation is not supported")
    }

    override fun getType(uri: Uri): String? {
        throw UnsupportedOperationException("This operation is not supported")
    }

    /**
     * Return the current content of this widget, as a short list of rows. Called every time the
     * launcher refreshes the widget (on a background thread).
     * @param lang the ISO 639 language code of the language that the user has set for the
     * launcher. Should be used for any localized text if supported by the plugin.
     */
    abstract suspend fun getWidgetItems(lang: String): List<PluginWidgetItem>
}
