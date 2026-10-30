package de.mm20.launcher2.comms.clock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import de.mm20.launcher2.comms.R

/** Owns the alarm independently of the Clock composable or launcher process UI lifecycle. */
class ClockAlarmService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "dismiss") { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("clock_alarms", getString(de.mm20.launcher2.i18n.R.string.clock_alarm), NotificationManager.IMPORTANCE_HIGH))
        val stop = PendingIntent.getService(this, 0, Intent(this, ClockAlarmService::class.java).setAction("dismiss"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(7141, NotificationCompat.Builder(this, "clock_alarms")
            .let { de.mm20.launcher2.search.VirtualAppAppearance.decorate(it, this, "telos_clock_app") }
            .setContentTitle(intent?.getStringExtra("label").takeUnless { it.isNullOrBlank() } ?: getString(de.mm20.launcher2.i18n.R.string.clock_alarm))
            .setContentText(getString(de.mm20.launcher2.i18n.R.string.clock_dismiss)).setContentIntent(stop)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setOngoing(true)
            .addAction(0, getString(de.mm20.launcher2.i18n.R.string.clock_dismiss), stop).build())
        play(intent?.getStringExtra("uri").orEmpty(), fallback = false)
        handler.postDelayed({ stopSelf() }, 10 * 60_000L)
        return START_NOT_STICKY
    }
    private fun play(uri: String, fallback: Boolean) {
        player?.release()
        player = null
        val source = if (uri.isBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(uri)
        val candidate = MediaPlayer()
        player = candidate
        val timeout = Runnable { if (player === candidate) { if (!fallback) play("", true) else stopSelf() } }
        try {
            candidate.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            candidate.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            candidate.isLooping = true
            candidate.setOnPreparedListener { handler.removeCallbacks(timeout); it.start() }
            candidate.setOnErrorListener { _, _, _ ->
                handler.removeCallbacks(timeout)
                if (!fallback) play("", true) else stopSelf()
                true
            }
            candidate.setDataSource(this, source)
            handler.postDelayed(timeout, 15_000)
            candidate.prepareAsync()
        } catch (_: Exception) {
            handler.removeCallbacks(timeout)
            if (!fallback) play("", true) else stopSelf()
        }
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release(); player = null
        super.onDestroy()
    }
}
