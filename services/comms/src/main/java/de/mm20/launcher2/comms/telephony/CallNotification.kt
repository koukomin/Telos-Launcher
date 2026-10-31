package de.mm20.launcher2.comms.telephony
import de.mm20.launcher2.i18n.R as I18nR

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.Person
import androidx.core.app.NotificationCompat

object CallNotification {
    private const val CHANNEL_ID = "telos_incall"
    private const val NOTIFICATION_ID = 7201

    private var alertedIncomingToken = 0L

    fun show(context: Context, state: InCallUiState, fullScreenVisible: Boolean = false) {
        val live = TelosCallSession.ui.value
        if (!live.hasCall) { alertedIncomingToken = 0L; cancel(context); return }
        // Ignore stale emissions without cancelling a newer, legitimate call notification.
        if (!currentNotificationState(state, live)) return
        val alertIncoming = state.incoming && alertedIncomingToken != state.actionToken
        if (state.incoming) alertedIncomingToken = state.actionToken
        if (callNotificationKind(state, fullScreenVisible, alertIncoming) == CallNotificationKind.None) {
            cancel(context)
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val dndFilter = manager.currentInterruptionFilter
        val isDndActive = dndFilter != NotificationManager.INTERRUPTION_FILTER_ALL && dndFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        val importance = if (isDndActive) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH

        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID,
            context.getString(I18nR.string.au_phoneb_calls_channel), importance))
        try {
            manager.notify(NOTIFICATION_ID, create(context, state, alertIncoming))
        } catch (e: SecurityException) {
            android.util.Log.w("TelosCallNotification", "Call notification permission unavailable", e)
        } catch (e: IllegalArgumentException) {
            // An OEM may still reject the full-screen call presentation. Preserve actionable controls.
            android.util.Log.w("TelosCallNotification", "Call presentation rejected; using ordinary actions", e)
            runCatching { manager.notify(NOTIFICATION_ID, create(context, state, false)) }
        }
    }

    /** Builds only; posting stays tied to authoritative live-call state in [show]. */
    fun create(context: Context, state: InCallUiState, alertIncoming: Boolean): android.app.Notification {
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
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(de.mm20.launcher2.search.VirtualAppAppearance.glyph("telos_phone_app") ?: android.R.drawable.stat_sys_phone_call)
            .setContentTitle(title)
            .setContentText(if (state.likelySpam) context.getString(I18nR.string.caller_likely_spam) + " · " + text else text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        when (callNotificationKind(state, false, alertIncoming)) {
            CallNotificationKind.IncomingFullScreen -> {
                val person = Person.Builder().setName(title).setImportant(true).build()
                builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(person, action("reject"), action("answer")))
                    .setFullScreenIntent(pending, true)
            }
            CallNotificationKind.IncomingActions -> builder
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(I18nR.string.comms_reject), action("reject"))
                .addAction(android.R.drawable.sym_action_call, context.getString(I18nR.string.comms_answer), action("answer"))
            CallNotificationKind.OngoingActions -> {
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(I18nR.string.comms_hangup), action("hangup"))
                state.connectedAtEpochMs?.let { builder.setWhen(it).setShowWhen(true).setUsesChronometer(state.active) }
            }
            CallNotificationKind.None -> Unit
        }
        return de.mm20.launcher2.search.VirtualAppAppearance.decorate(builder, context, "telos_phone_app").build()
    }

    /** Recover an orphan left by a previous process crash, without removing a real Telecom call. */
    suspend fun clearOrphaned(context: Context) {
        val appContext = context.applicationContext
        val inCall = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { appContext.getSystemService(android.telecom.TelecomManager::class.java)?.isInCall }.getOrNull()
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            if (clearOrphanCallNotification(inCall, TelosCallSession.ui.value.hasCall)) {
                alertedIncomingToken = 0L
                cancel(appContext)
            }
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
