package de.mm20.launcher2.ui.voice

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
import androidx.core.content.ContextCompat
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and shows the notification while Telos Voice Recorder records, so that
 * a recording goes on with the screen off or in another app. The recording itself is done by
 * [VoiceRecorderEngine].
 */
class VoiceRecorderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                VoiceRecorderEngine.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                if (VoiceRecorderEngine.state.value.status == VoiceStatus.Paused) VoiceRecorderEngine.resume()
                else VoiceRecorderEngine.pause()
            }
            else -> {
                startInForeground()
                if (!VoiceRecorderEngine.start(applicationContext)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        if (observer == null) {
            observer = scope.launch {
                VoiceRecorderEngine.state.collect { state ->
                    if (state.status == VoiceStatus.Idle) {
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state.status == VoiceStatus.Paused))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification(paused = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(paused: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.voice_notification_channel), NotificationManager.IMPORTANCE_LOW))
        }
        fun action(action: String, request: Int) = PendingIntent.getService(
            this, request, Intent(this, VoiceRecorderService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, SettingsActivity::class.java).apply {
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_VOICE_RECORDER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_glyph_voice_recorder)
            .setContentTitle(getString(if (paused) R.string.voice_notification_paused else R.string.voice_notification_recording))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(if (paused) R.string.voice_resume else R.string.voice_pause), action(ACTION_TOGGLE_PAUSE, 1))
            .addAction(0, getString(R.string.voice_stop), action(ACTION_STOP, 2))
            .build()
    }

    override fun onDestroy() {
        // a recording that is still running when the service goes away is kept
        if (VoiceRecorderEngine.state.value.status != VoiceStatus.Idle) VoiceRecorderEngine.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "voice_recorder"
        private const val NOTIFICATION_ID = 7410
        const val ACTION_STOP = "de.mm20.launcher2.action.VOICE_STOP"
        const val ACTION_TOGGLE_PAUSE = "de.mm20.launcher2.action.VOICE_TOGGLE_PAUSE"

        /** Starts recording: the service starts the foreground notification and then the engine */
        fun startRecording(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, VoiceRecorderService::class.java))
        }

        fun stopRecording(context: Context) {
            context.startService(Intent(context, VoiceRecorderService::class.java).setAction(ACTION_STOP))
        }
    }
}
