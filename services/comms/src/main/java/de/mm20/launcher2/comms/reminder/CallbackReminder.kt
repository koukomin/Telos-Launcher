package de.mm20.launcher2.comms.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * "Remind me" for calls: posts a notification after a delay that opens the dialer
 * pre-filled with the number.
 */
object CallbackReminder {
    const val EXTRA_NAME = "de.mm20.launcher2.comms.REMINDER_NAME"
    const val EXTRA_NUMBER = "de.mm20.launcher2.comms.REMINDER_NUMBER"

    private fun pendingIntent(context: Context, number: String, name: String?, flags: Int): PendingIntent? {
        val intent = Intent(context, CallbackReminderReceiver::class.java).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_NUMBER, number)
            putExtra(EXTRA_NAME, name)
        }
        return PendingIntent.getBroadcast(
            context,
            number.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or flags,
        )
    }

    fun schedule(context: Context, number: String, name: String?, delayMinutes: Int) {
        if (number.isBlank()) return
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, number, name, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val trigger = System.currentTimeMillis() + delayMinutes.coerceAtLeast(1) * 60_000L
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }
}
