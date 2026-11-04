package de.mm20.launcher2.appmanagement.shizuku

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shizuku-style wireless pairing: a foreground notification searches `_adb-tls-pairing._tcp`,
 * then asks for the 6-digit code via [RemoteInput] once the Settings pairing dialog is up.
 */
class TelosAdbPairingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var discovery: TelosAdbPairingDiscovery? = null
    private var foundHost: String? = null
    private var foundPort: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(I18nR.string.telos_shizuku_pair_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    setSound(null, null)
                    setShowBadge(false)
                },
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSearch()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REPLY -> {
                val code = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_CODE)?.toString().orEmpty()
                val host = intent.getStringExtra(EXTRA_HOST) ?: foundHost
                val port = intent.getIntExtra(EXTRA_PORT, foundPort ?: -1).takeIf { it > 0 }
                show(workingNotification())
                scope.launch {
                    TelosAdbPairingAccessibilityService.pendingFill = code.filter { it.isDigit() }.take(6)
                    val r = TelosShizukuHost.submitPairingCode(this@TelosAdbPairingService, code)
                    show(if (r.isSuccess) doneNotification() else failNotification(r.exceptionOrNull()?.message))
                    if (r.isSuccess) {
                        stopSearch()
                        stopSelf()
                    }
                }
                return START_REDELIVER_INTENT
            }
            else -> {
                TelosShizukuHost.notifyPairingSearch()
                startSearch()
                show(searchingNotification())
                openWirelessDebugging(this)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        stopSearch()
        scope.cancel()
        super.onDestroy()
    }

    private fun startSearch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (discovery != null) return
        val d = TelosAdbPairingDiscovery(this) { host, port ->
            foundHost = host
            foundPort = port
            TelosShizukuHost.onPairingCodeRead(this, TelosShizukuHost.status.value.pairing.code, host, port)
            show(inputNotification(host, port))
        }
        discovery = d
        d.start()
    }

    private fun stopSearch() {
        discovery?.stop()
        discovery = null
        TelosShizukuHost.stopPairingWait()
    }

    private fun show(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, n)
        }
        getSystemService(NotificationManager::class.java)?.notify(ID, n)
    }

    private fun base(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(BaseR.drawable.ic_glyph_system)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openSystemPending())

    private fun searchingNotification(): Notification = base()
        .setContentTitle(getString(I18nR.string.telos_shizuku_pair_notif_searching))
        .setContentText(getString(I18nR.string.telos_shizuku_pair_notif_searching_text))
        .addAction(stopAction())
        .build()

    private fun inputNotification(host: String, port: Int): Notification {
        val reply = PendingIntent.getService(
            this,
            2,
            Intent(this, TelosAdbPairingService::class.java)
                .setAction(ACTION_REPLY)
                .putExtra(EXTRA_HOST, host)
                .putExtra(EXTRA_PORT, port),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val remote = RemoteInput.Builder(KEY_CODE)
            .setLabel(getString(I18nR.string.telos_shizuku_pair_code))
            .build()
        val action = NotificationCompat.Action.Builder(
            0,
            getString(I18nR.string.telos_shizuku_pair_notif_enter),
            reply,
        ).addRemoteInput(remote).build()
        return base()
            .setContentTitle(getString(I18nR.string.telos_shizuku_pair_notif_found))
            .setContentText(getString(I18nR.string.telos_shizuku_pair_found, host, port))
            .addAction(action)
            .addAction(stopAction())
            .build()
    }

    private fun workingNotification(): Notification = base()
        .setContentTitle(getString(I18nR.string.telos_shizuku_pair_working))
        .build()

    private fun doneNotification(): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(BaseR.drawable.ic_glyph_system)
        .setContentTitle(getString(I18nR.string.telos_shizuku_pair_ok))
        .setAutoCancel(true)
        .setContentIntent(openSystemPending())
        .build()

    private fun failNotification(detail: String?): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(BaseR.drawable.ic_glyph_system)
        .setContentTitle(getString(I18nR.string.telos_shizuku_pair_fail))
        .setContentText(detail)
        .setAutoCancel(true)
        .addAction(
            NotificationCompat.Action.Builder(
                0,
                getString(I18nR.string.telos_shizuku_pair_start),
                PendingIntent.getService(
                    this, 3, startIntent(this),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build(),
        )
        .build()

    private fun stopAction(): NotificationCompat.Action = NotificationCompat.Action.Builder(
        0,
        getString(I18nR.string.telos_shizuku_pair_stop),
        PendingIntent.getService(
            this, 4, stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    ).build()

    private fun openSystemPending(): PendingIntent {
        val open = Intent().setClassName(packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
            .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_SYSTEM)
            .putExtra(SettingsDeepLinkContract.EXTRA_SYSTEM_TAB, "shizuku")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            this, 5, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL = "telos_adb_pairing"
        const val ID = 0xADB1
        private const val ACTION_START = "de.mm20.launcher2.shizuku.PAIR_START"
        private const val ACTION_STOP = "de.mm20.launcher2.shizuku.PAIR_STOP"
        private const val ACTION_REPLY = "de.mm20.launcher2.shizuku.PAIR_REPLY"
        private const val KEY_CODE = "pairing_code"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"

        fun startIntent(context: Context) =
            Intent(context, TelosAdbPairingService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context) =
            Intent(context, TelosAdbPairingService::class.java).setAction(ACTION_STOP)

        fun start(context: Context) {
            val app = context.applicationContext
            val i = startIntent(app)
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i) else app.startService(i)
        }

        fun openWirelessDebugging(context: Context) {
            val flags = Intent.FLAG_ACTIVITY_NEW_TASK
            val tries = listOf(
                Intent("com.android.settings.WIFI_DEBUGGING_SETTINGS").addFlags(flags),
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(flags),
                Intent().setClassName("com.android.settings", "com.android.settings.Settings\$DevelopmentSettingsDashboardActivity").addFlags(flags),
                Intent().setClassName("com.android.settings", "com.android.settings.development.WirelessDebuggingActivity").addFlags(flags),
            )
            for (intent in tries) {
                if (runCatching { context.startActivity(intent) }.isSuccess) return
            }
        }
    }
}
