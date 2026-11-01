package de.mm20.launcher2.appmanagement.shizuku

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.coroutines.TelosDispatchers
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.preferences.ui.UiSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TelosShizukuWatchdog : Service(), KoinComponent {
    private val ui: UiSettings by inject()
    private val scope = CoroutineScope(SupervisorJob() + TelosDispatchers.Disk)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(I18nR.string.telos_shizuku_watchdog_channel), NotificationManager.IMPORTANCE_MIN),
            )
        }
        startForeground(
            0x5140,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(BaseR.drawable.ic_glyph_system)
                .setContentTitle(getString(I18nR.string.telos_shizuku_watchdog_title))
                .setOngoing(true)
                .build(),
        )
        scope.launch {
            while (isActive) {
                val auto = ui.shizukuAutoStart.first()
                val method = TelosShizukuHost.StartMethod.Auto
                TelosShizukuHost.watchdogTick(this@TelosShizukuWatchdog, auto, method)
                delay(30_000)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "telos_shizuku_watchdog"
        fun start(context: Context) {
            val i = Intent(context, TelosShizukuWatchdog::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, TelosShizukuWatchdog::class.java))
        }
    }
}

class TelosShizukuBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        TelosShizukuWatchdog.start(context.applicationContext)
    }
}

class TelosShizukuStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        TelosShizukuWatchdog.start(context.applicationContext)
    }
}
