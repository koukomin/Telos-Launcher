package de.mm20.launcher2.comms.fakecall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class FakeCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(FakeCallScheduler.EXTRA_NAME) ?: "Incoming call"
        val number = intent.getStringExtra(FakeCallScheduler.EXTRA_NUMBER).orEmpty()
        val activity = Intent().setClassName(context.packageName, FakeCallScheduler.ACTIVITY).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(FakeCallScheduler.EXTRA_NAME, name)
            putExtra(FakeCallScheduler.EXTRA_NUMBER, number)
        }
        val pending = PendingIntent.getActivity(
            context,
            9999,
            activity,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channelId = "telos_fake_call"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Fake call", NotificationManager.IMPORTANCE_HIGH).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .build(),
                    )
                }
            )
        }
        nm.notify(
            9999,
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle(name)
                .setContentText(if (number.isBlank()) "Incoming call" else "Incoming call ($number)")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(pending, true)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build(),
        )
        try {
            context.startActivity(activity)
        } catch (_: Exception) {
        }
    }
}
