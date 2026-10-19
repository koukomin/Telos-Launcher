package de.mm20.launcher2.network.vpn

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import de.mm20.launcher2.network.NetworkEngine
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.TimeUnit

/** Persisted intent is authoritative: stale alarms/work can never turn a stopped VPN back on. */
internal object VpnPauseScheduler {
    private const val WORK = "telos-vpn-resume"
    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, 7103, Intent(context, VpnResumeReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
    fun schedule(context: Context, deadline: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(context)
        manager.cancel(intent)
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
            try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, intent) }
            catch (_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, intent) }
        } else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, intent)
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<VpnResumeWorker>()
                .setInitialDelay((deadline - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build())
    }
}

class VpnResumeReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent?) {
        get<NetworkEngine>().restoreIfWanted()
    }
}

class VpnResumeWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters), KoinComponent {
    override suspend fun doWork(): Result {
        get<NetworkEngine>().restoreIfWanted()
        return Result.success()
    }
}

/** Notification actions use a receiver so Stop never starts a new foreground service. */
class VpnNotificationReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP -> get<NetworkEngine>().stop()
        }
    }
    companion object { const val ACTION_STOP = "de.mm20.launcher2.network.NOTIFICATION_STOP" }
}
