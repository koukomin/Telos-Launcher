package de.mm20.launcher2.comms.telephony

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import de.mm20.launcher2.i18n.R as I18nR

object CallNotification {
    private const val CHANNEL_ID = "telos_incall"
    private const val NOTIFICATION_ID = 7102

    fun show(context: Context, state: InCallUiState) {
        if (!state.hasCall) {
            cancel(context)
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(I18nR.string.au_phoneb_calls_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        val fullScreen = Intent().setClassName(context.packageName, TelosDialer.CALL_ACTIVITY)
        fullScreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context,
            0,
            fullScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = state.name ?: state.number.ifEmpty { context.getString(I18nR.string.au_phoneb_call_title_fallback) }
        val text = context.getString(
            when {
                state.incoming -> I18nR.string.comms_incoming_call
                state.connecting -> I18nR.string.comms_calling
                state.onHold -> I18nR.string.comms_on_hold
                else -> I18nR.string.au_phoneb_ongoing_call
            }
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, state.incoming)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
