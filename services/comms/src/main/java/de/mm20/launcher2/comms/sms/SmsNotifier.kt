package de.mm20.launcher2.comms.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.comms.privacy.HiddenContacts
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.first

/** The notification for a received message. As the default SMS app Telos has to show it. */
object SmsNotifier {
    private const val CHANNEL = "telos_messages"

    suspend fun incoming(context: Context, settings: CommsSettings, address: String, text: String) {
        val hidden = runCatching { HiddenContacts.matches(address, settings.hiddenNumbers.first()) }.getOrDefault(false)
        // a hidden contact is not named, and the text is not shown
        val title = if (hidden) "New message" else (SmsThreads.displayName(context, address) ?: address)
        show(context, address, title, if (hidden) "" else text)
    }

    fun show(context: Context, address: String, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, address.hashCode(),
            Intent().setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_COMMS)
                .putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, "messages")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            address.hashCode(),
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(de.mm20.launcher2.base.R.drawable.sms_24px)
                .setContentTitle(title)
                .apply { if (text.isNotBlank()) setContentText(text) }
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .build()
        )
    }
}
