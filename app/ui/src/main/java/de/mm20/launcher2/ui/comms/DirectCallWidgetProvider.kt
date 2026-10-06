package de.mm20.launcher2.ui.comms

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.ui.R

class DirectCallWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent().setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_COMMS)
                .putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, "dialpad")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_direct_call)
            views.setOnClickPendingIntent(R.id.widget_direct_call_root, open)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
