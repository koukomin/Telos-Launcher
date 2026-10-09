package de.mm20.launcher2.ui.comms

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.widget.RemoteViews
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.comms.privacy.HiddenContacts
import de.mm20.launcher2.comms.privacy.PrivacySession
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RecentsWidgetProvider : AppWidgetProvider(), KoinComponent {

    private val commsSettings: CommsSettings by inject()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
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
                    views.setTextViewText(
                        R.id.widget_recents_body,
                        lines.ifBlank { context.getString(R.string.recents_empty_title) },
                    )
                    views.setOnClickPendingIntent(R.id.widget_recents_title, open)
                    views.setOnClickPendingIntent(R.id.widget_recents_body, open)
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun query(context: Context): String {
        // numbers the user hid from the recents must not show up on the home screen either
        val hidden = runCatching {
            if (commsSettings.hideFromRecents.first() && !PrivacySession.hiderUnlocked.value) {
                commsSettings.hiddenNumbers.first()
            } else emptyMap()
        }.getOrDefault(emptyMap())
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER),
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                val names = mutableListOf<String>()
                while (names.size < 4 && c.moveToNext()) {
                    val number = c.getString(1).orEmpty()
                    if (HiddenContacts.matches(number, hidden)) continue
                    names += c.getString(0)?.ifBlank { null } ?: number
                }
                names.joinToString("\n")
            }.orEmpty()
        } catch (_: SecurityException) {
            context.getString(R.string.au_phonea_grant_calllog)
        } catch (_: RuntimeException) {
            ""
        }
    }
}
