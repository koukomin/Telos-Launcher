package de.mm20.launcher2.comms.reminder
import de.mm20.launcher2.i18n.R as I18nR

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class CallbackReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(CallbackReminder.EXTRA_NUMBER).orEmpty()
        if (number.isBlank()) return
        val name = intent.getStringExtra(CallbackReminder.EXTRA_NAME)?.takeIf { it.isNotBlank() } ?: number
        val dial = Intent().apply {
            component = android.content.ComponentName(
                context.packageName,
                de.mm20.launcher2.applock.SettingsDeepLinkContract.ACTIVITY_CLASS_NAME,
            )
            putExtra(
                de.mm20.launcher2.applock.SettingsDeepLinkContract.EXTRA_ROUTE,
                de.mm20.launcher2.applock.SettingsDeepLinkContract.ROUTE_COMMS,
            )
            putExtra(de.mm20.launcher2.applock.SettingsDeepLinkContract.EXTRA_COMMS_TAB, "dialpad")
            putExtra(de.mm20.launcher2.applock.SettingsDeepLinkContract.EXTRA_DIAL_NUMBER, number)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            context,
            number.hashCode(),
            dial,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channelId = "telos_callback_reminder"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, context.getString(I18nR.string.au_phoneb_reminder_channel), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        nm.notify(
            number.hashCode(),
            NotificationCompat.Builder(context, channelId)
                .let { de.mm20.launcher2.search.VirtualAppAppearance.decorate(it, context, "telos_phone_app") }
                .setContentTitle(context.getString(I18nR.string.au_phoneb_reminder_title, name))
                .setContentText(number)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pending)
                .addAction(android.R.drawable.sym_action_call, context.getString(I18nR.string.hc_call), pending)
                .setAutoCancel(true)
                .build(),
        )
    }
}
