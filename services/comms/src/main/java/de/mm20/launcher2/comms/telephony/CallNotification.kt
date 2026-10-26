package de.mm20.launcher2.comms.telephony

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.Person
import androidx.core.app.NotificationCompat
import de.mm20.launcher2.i18n.R as I18nR

object CallNotification {
    private const val CHANNEL_ID = "telos_incall"
    private const val NOTIFICATION_ID = 7201

    private var alertedIncomingToken = 0L

    fun show(context: Context, state: InCallUiState, fullScreenVisible: Boolean = false) {
        if (!showCallNotification(state.hasCall, fullScreenVisible)) {
            if (state.incoming) alertedIncomingToken = state.actionToken
            if (!state.hasCall) alertedIncomingToken = 0L
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
        val caller = state.name?.takeIf { it.isNotBlank() } ?: state.number.ifEmpty { context.getString(I18nR.string.au_phoneb_call_title_fallback) }
        val title = if (state.callerNameSuggested) context.getString(I18nR.string.caller_suggested_name, caller) else caller
        val text = context.getString(
            when {
                state.incoming -> I18nR.string.comms_incoming_call
                state.connecting -> I18nR.string.comms_calling
                state.onHold -> I18nR.string.comms_on_hold
                else -> I18nR.string.au_phoneb_ongoing_call
            }
        )
        fun action(action: String): PendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode(), Intent(context, CallActionReceiver::class.java)
                .setData(android.net.Uri.parse("telos-call://${state.actionToken}/$action"))
                .putExtra("call_action", action).putExtra("call_token", state.actionToken),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val person = Person.Builder().setName(title).setImportant(true).build()
        val style = if (state.incoming) NotificationCompat.CallStyle.forIncomingCall(person, action("reject"), action("answer"))
            else NotificationCompat.CallStyle.forOngoingCall(person, action("hangup"))
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(de.mm20.launcher2.search.VirtualAppAppearance.glyph("telos_phone_app") ?: android.R.drawable.stat_sys_phone_call)
            .setContentTitle(title)
            .setContentText(if (state.likelySpam) context.getString(I18nR.string.caller_likely_spam) + " · " + text else text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setStyle(style)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        if (state.incoming && alertedIncomingToken != state.actionToken) {
            builder.setFullScreenIntent(pending, true)
            alertedIncomingToken = state.actionToken
        }
        try {
            manager.notify(NOTIFICATION_ID, de.mm20.launcher2.search.VirtualAppAppearance.decorate(builder, context, "telos_phone_app").build())
        } catch (e: SecurityException) {
            android.util.Log.w("TelosCallNotification", "Call notification permission unavailable", e)
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("TelosCallNotification", "Call notification rejected by system", e)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
