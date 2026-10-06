package de.mm20.launcher2.ui.comms

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.widget.RemoteViews
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.ui.R

class RecentsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val lines = query(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent().setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_COMMS)
                .putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, "recents")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_recents)
            views.setTextViewText(R.id.widget_recents_body, lines.ifBlank { "No recent calls" })
            views.setOnClickPendingIntent(R.id.widget_recents_title, open)
            views.setOnClickPendingIntent(R.id.widget_recents_body, open)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun query(context: Context): String {
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 4",
            )?.use { c ->
                buildString {
                    while (c.moveToNext()) {
                        val name = c.getString(0)?.ifBlank { null } ?: c.getString(1).orEmpty()
                        if (isNotEmpty()) append('\n')
                        append(name)
                    }
                }
            }.orEmpty()
        } catch (_: SecurityException) {
            "Grant call log permission"
        }
    }
}
