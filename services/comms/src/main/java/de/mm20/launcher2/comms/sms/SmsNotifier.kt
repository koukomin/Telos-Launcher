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
        val snap = runCatching { settings.snapshot.first() }.getOrNull()
        val title = if (hidden) {
            if (snap?.hiddenNotifDisguised == true) snap.hiddenNotifTitle
            else SmsText.get(context, "hc_new_message", "New message")
        } else (SmsThreads.displayName(context, address) ?: address)

        val notifText = if (hidden) {
            if (snap?.hiddenNotifDisguised == true) snap.hiddenNotifText else ""
        } else text

        show(context, address, title, notifText)
        if (!hidden && settings.smsPopupReply.first()) {
            runCatching {
                context.startActivity(
                    Intent().setClassName(context.packageName, "de.mm20.launcher2.ui.comms.SmsPopupActivity")
                        .putExtra("address", address)
                        .putExtra("title", title)
                        .putExtra("text", text)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
        }
    }

    fun show(context: Context, address: String, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, SmsText.get(context, "hc_messages", "Messages"), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, address.hashCode(),
            Intent().setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_COMMS)
                .putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, "messages")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val reply = PendingIntent.getBroadcast(
            context, address.hashCode() + 1,
            Intent(context, SmsNotifActionReceiver::class.java)
                .setAction(SmsNotifActionReceiver.REPLY)
                .putExtra("address", address),
            flags,
        )
        val markRead = PendingIntent.getBroadcast(
            context, address.hashCode() + 2,
            Intent(context, SmsNotifActionReceiver::class.java)
                .setAction(SmsNotifActionReceiver.READ)
                .putExtra("address", address),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val speak = PendingIntent.getBroadcast(
            context, address.hashCode() + 3,
            Intent(context, SmsNotifActionReceiver::class.java)
                .setAction(SmsNotifActionReceiver.SPEAK)
                .putExtra("address", address)
                .putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val remote = android.app.RemoteInput.Builder("reply").setLabel(SmsText.get(context, "hc_message", "Message")).build()
        nm.notify(
            address.hashCode(),
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(
                    de.mm20.launcher2.search.VirtualAppAppearance.glyph("telos_messages_app")
                        ?: de.mm20.launcher2.base.R.drawable.sms_24px,
                )
                .apply {
                    de.mm20.launcher2.search.VirtualAppAppearance.largeIcon(context, "telos_messages_app")
                        ?.let { setLargeIcon(it) }
                }
                .setContentTitle(title)
                .apply { if (text.isNotBlank()) setContentText(text) }
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setFullScreenIntent(open, true)
                .addAction(
                    Notification.Action.Builder(null, SmsText.get(context, "hc_send", "Reply"), reply)
                        .addRemoteInput(remote)
                        .build(),
                )
                .addAction(Notification.Action.Builder(null, SmsText.get(context, "comms_mark_read", "Read"), markRead).build())
                .addAction(Notification.Action.Builder(null, SmsText.get(context, "comms_speak", "Speak"), speak).build())
                .build()
        )
    }
}
