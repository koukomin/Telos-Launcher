package de.mm20.launcher2.sdk.gestures

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.contracts.GestureActionPluginContract
import de.mm20.launcher2.plugin.contracts.GestureActionPluginContract.ActionColumns
import de.mm20.launcher2.plugin.data.buildCursor
import de.mm20.launcher2.sdk.base.BasePluginProvider
import de.mm20.launcher2.sdk.utils.launchWithCancellationSignal
import kotlinx.coroutines.runBlocking

/**
 * An action that can be bound to a launcher gesture (e.g. swipe up, double tap).
 * @param id A stable identifier for this action, unique within this plugin. Passed back to
 * [GestureActionProvider.invokeAction] when the user triggers the gesture.
 * @param label A short, human-readable label shown in the launcher's gesture picker.
 */
data class PluginGestureAction(
    val id: String,
    val label: String,
)

/**
 * Base class for [PluginType.GestureAction] plugins: exposes a small, fixed list of named
 * actions that the user can bind to a launcher gesture.
 */
abstract class GestureActionProvider : BasePluginProvider() {
    override fun getPluginType(): PluginType {
        return PluginType.GestureAction
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

        if (uri.pathSegments.size == 1 && uri.pathSegments.first() == GestureActionPluginContract.Paths.Actions) {
            val actions = launchWithCancellationSignal(cancellationSignal) {
                getActions()
            }
            return buildCursor(ActionColumns, actions) {
                put(ActionColumns.Id, it.id)
                put(ActionColumns.Label, it.label)
            }
        }
        return null
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == GestureActionPluginContract.Methods.Invoke) {
            val context = context ?: return null
            checkPermissionOrThrow(context)
            val actionId = arg ?: return null
            runBlocking { invokeAction(actionId) }
            return Bundle.EMPTY
        }
        return super.call(method, arg, extras)
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
     * Return the list of actions this plugin currently offers. May change over time (e.g. based
     * on plugin configuration) - the launcher re-queries this whenever the user opens the
     * gesture picker.
     */
    abstract suspend fun getActions(): List<PluginGestureAction>

    /**
     * Called when the user triggers a gesture that is bound to the action with this [actionId].
     * Called on a background thread; do not block for a long time, and do not attempt to launch
     * UI that expects to be in the foreground without appropriate PendingIntent/task handling.
     */
    abstract suspend fun invokeAction(actionId: String)
}
