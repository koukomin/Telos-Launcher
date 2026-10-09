package de.mm20.launcher2.comms.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import de.mm20.launcher2.i18n.R as I18nR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Foreground service that keeps the process alive and shows a notification with a Stop action while recording. */
class RadioRecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            RadioRecorder.stop()
        }
        val name = intent?.getStringExtra(EXTRA_NAME).orEmpty()
        val startedAt = intent?.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis()) ?: System.currentTimeMillis()
        if (intent?.action != ACTION_STOP) {
            val notification = buildNotification(name, startedAt)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        scope.launch {
            // the recording ends by itself (stream dropped, storage full) or by the Stop action
            RadioRecorder.state.first { it is RadioRecorder.State.Idle }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(station: String, startedAt: Long): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(I18nR.string.au2_radio2_notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    description = getString(I18nR.string.au2_radio2_notif_channel_desc)
                }
            )
        }
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, RadioRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(I18nR.string.au2_radio2_notif_title, station))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setWhen(startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .addAction(0, getString(I18nR.string.au2_radio2_notif_stop), stop)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_NAME = "station_name"
        const val EXTRA_STARTED_AT = "started_at"
        const val ACTION_STOP = "de.mm20.launcher2.comms.radio.STOP_RECORDING"
        private const val CHANNEL = "radio_recording"
        private const val NOTIFICATION_ID = 7412
    }
}
