package de.mm20.launcher2.downloads

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Keeps the process alive and shows the notifications while there are downloads. The downloads run in the
 * [DownloadManager]; this service only holds the foreground notification (type dataSync), a wake lock,
 * and handles the notification actions. It stops itself a few seconds after the manager says there is
 * nothing left to do.
 */
class DownloadService : Service(), KoinComponent {

    private val manager: DownloadManager by inject()
    private val notifier: DownloadNotifier by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // must be called within a few seconds of startForegroundService, whatever the intent says
        if (!startInForeground()) {
            // Android 12+ refuses this when the app is not allowed to start a foreground service right now
            stopSelf()
            return START_NOT_STICKY
        }
        manager.start()
        when (intent?.action) {
            ACTION_PAUSE -> intent.getStringExtra(EXTRA_TASK_ID)?.let(manager::pause)
            ACTION_RESUME -> intent.getStringExtra(EXTRA_TASK_ID)?.let { notifier.clearResult(it); manager.resume(it) }
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_TASK_ID)?.let { manager.remove(it, deleteFile = false) }
            ACTION_PAUSE_ALL -> manager.pauseAll()
            ACTION_RESUME_ALL -> manager.resumeAll()
        }
        acquireWakeLock()
        if (observer == null) {
            observer = scope.launch {
                var idleSince = 0L
                combine(manager.tasks, manager.blockReason, manager.serviceWanted) { t, b, w -> Triple(t, b, w) }.collect { (tasks, blocked, wanted) ->
                    notifier.updateTasks(tasks)
                    getSystemService(android.app.NotificationManager::class.java)
                        .notify(DownloadNotifier.SUMMARY_ID, notifier.summary(tasks, blocked))
                    if (!wanted) {
                        if (idleSince == 0L) {
                            idleSince = System.currentTimeMillis()
                            launch {
                                delay(IDLE_STOP_MS)
                                if (!manager.serviceWanted.value) stopSelf()
                            }
                        }
                    } else idleSince = 0L
                    // progress changes several times per second; Android drops notification updates that come too often
                    delay(NOTIFY_INTERVAL_MS)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(): Boolean = try {
        val notification = notifier.summary(manager.tasks.value, manager.blockReason.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, DownloadNotifier.SUMMARY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(DownloadNotifier.SUMMARY_ID, notification)
        }
        true
    } catch (e: Exception) {
        false
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "telos:downloads").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_MS)
        }
    }

    /**
     * Android 15 ends a dataSync foreground service after 6 hours a day. The running downloads are paused
     * (not failed); resuming them starts the service again.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        manager.pauseAllForSystem()
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        observer?.cancel()
        scope.cancel()
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        notifier.clearTaskNotifications()
        getSystemService(android.app.NotificationManager::class.java).cancel(DownloadNotifier.SUMMARY_ID)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TASK_ID = "de.mm20.launcher2.downloads.TASK_ID"
        const val ACTION_PAUSE = "de.mm20.launcher2.downloads.PAUSE"
        const val ACTION_RESUME = "de.mm20.launcher2.downloads.RESUME"
        const val ACTION_CANCEL = "de.mm20.launcher2.downloads.CANCEL"
        const val ACTION_PAUSE_ALL = "de.mm20.launcher2.downloads.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "de.mm20.launcher2.downloads.RESUME_ALL"
        private const val IDLE_STOP_MS = 4_000L
        private const val NOTIFY_INTERVAL_MS = 500L

        /** True while an instance of the service exists */
        @Volatile
        var running = false
            private set
        private const val WAKE_LOCK_MS = 6L * 60 * 60 * 1000

        /** Starts the service; Android refuses this when the app is in the background, then the downloads still run while the process lives */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
