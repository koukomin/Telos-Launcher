package de.mm20.launcher2.comms.fakecall

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object FakeCallScheduler {
    const val EXTRA_NAME = "de.mm20.launcher2.comms.FAKE_CALLER_NAME"
    const val EXTRA_NUMBER = "de.mm20.launcher2.comms.FAKE_CALLER_NUMBER"
    const val ACTIVITY = "de.mm20.launcher2.ui.comms.FakeCallActivity"
    private const val REQUEST = 9999

    fun schedule(context: Context, name: String, number: String, delaySeconds: Int) {
        cancel(context)
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, FakeCallReceiver::class.java).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_NUMBER, number)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val trigger = System.currentTimeMillis() + delaySeconds.coerceAtLeast(1) * 1000L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, FakeCallReceiver::class.java).apply {
            setPackage(context.packageName)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarm.cancel(pending)
        pending.cancel()
    }
}
