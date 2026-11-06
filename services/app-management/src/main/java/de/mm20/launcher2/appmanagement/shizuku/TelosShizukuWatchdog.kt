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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext

class TelosShizukuWatchdog : Service(), KoinComponent {
    private val ui: UiSettings by inject()
    private val scope = CoroutineScope(SupervisorJob() + TelosDispatchers.Disk)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Library instrumentation and secondary entry points may have no launcher DI container.
        if (GlobalContext.getOrNull() == null) {
            stopSelf()
            return
        }
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
            var failures = 0
            while (isActive) {
                if (!runCatching { ui.shizukuAutoStart.first() }.getOrDefault(false)) {
                    stopSelf()
                    break
                }
                runCatching {
                    TelosShizukuHost.watchdogTick(this@TelosShizukuWatchdog, true, TelosShizukuHost.StartMethod.Auto)
                }
                failures = if (TelosShizukuHost.status.value.running) 0 else (failures + 1).coerceAtMost(4)
                delay(if (failures == 0) 30_000L else (30_000L shl failures).coerceAtMost(300_000L))
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
            if (GlobalContext.getOrNull() == null) return
            val i = Intent(context, TelosShizukuWatchdog::class.java)
            // Boot/background FGS restrictions differ by Android/OEM. Opening the launcher retries.
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
            }
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
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000L) {
                    runCatching {
                        val app = context.applicationContext
                        val ui = org.koin.core.context.GlobalContext.get().get<UiSettings>()
                        if (ui.shizukuAutoStart.first()) TelosShizukuWatchdog.start(app)
                        de.mm20.launcher2.appmanagement.tweaks.TweaksApplier.applySavedOnBoot(app)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class TelosShizukuStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000L) {
                    runCatching {
                        if (org.koin.core.context.GlobalContext.get().get<UiSettings>().shizukuAutoStart.first()) {
                            TelosShizukuWatchdog.start(context.applicationContext)
                        }
                    }
                }
            } finally { pending.finish() }
        }
    }
}
