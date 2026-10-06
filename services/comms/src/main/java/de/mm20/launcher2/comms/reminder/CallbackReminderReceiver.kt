package de.mm20.launcher2.comms.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

class CallbackReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(CallbackReminder.EXTRA_NUMBER).orEmpty()
        if (number.isBlank()) return
        val name = intent.getStringExtra(CallbackReminder.EXTRA_NAME)?.takeIf { it.isNotBlank() } ?: number
        val dial = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                NotificationChannel(channelId, "Call reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        nm.notify(
            number.hashCode(),
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle("Call back $name")
                .setContentText(number)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pending)
                .addAction(android.R.drawable.sym_action_call, "Call", pending)
                .setAutoCancel(true)
                .build(),
        )
    }
}
