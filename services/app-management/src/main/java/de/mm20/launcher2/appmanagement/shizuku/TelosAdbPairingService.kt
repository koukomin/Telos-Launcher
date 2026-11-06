package de.mm20.launcher2.appmanagement.shizuku

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
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
        scope.launch {
            TelosShizukuHost.status.collect { state ->
                when (state.pairing.phase) {
                    TelosShizukuHost.PairingPhase.Done -> {
                        stopSearch()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    TelosShizukuHost.PairingPhase.Working -> show(workingNotification())
                    TelosShizukuHost.PairingPhase.Failed -> {
                        show(failNotification(state.lastLog))
                    }
                    else -> Unit
                }
            }
        }
        scope.launch {
            delay(180_000)
            TelosShizukuHost.cancelPairing()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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
                TelosShizukuHost.cancelPairing()
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
                    val r = TelosShizukuHost.submitPairingCode(this@TelosAdbPairingService, code, host, port)
                    show(if (r.isSuccess) doneNotification() else failNotification(r.exceptionOrNull()?.message))
                    if (r.isSuccess) {
                        stopSearch()
                        stopSelf()
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                discovery?.stop()
                discovery = null
                foundHost = null
                foundPort = null
                TelosShizukuHost.notifyPairingSearch(intent?.getBooleanExtra(EXTRA_AUTOMATIC, true) ?: true)
                startSearch()
                show(searchingNotification())
            }
            else -> return START_NOT_STICKY
        }
        return START_NOT_STICKY
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
            if (TelosShizukuHost.status.value.pairing.phase !in setOf(TelosShizukuHost.PairingPhase.Searching, TelosShizukuHost.PairingPhase.WaitingForCode)) return@TelosAdbPairingDiscovery
            if (foundHost == host && foundPort == port) return@TelosAdbPairingDiscovery
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
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openSystemPending())

    private fun searchingNotification(): Notification = base()
        .setContentTitle(getString(I18nR.string.telos_shizuku_pair_notif_searching))
        .setContentText(getString(I18nR.string.telos_shizuku_pair_notif_searching_text))
        .addAction(stopAction())
        .build()

    private fun inputNotification(host: String, port: Int): Notification {
        val reply = if (Build.VERSION.SDK_INT >= 26) {
            PendingIntent.getForegroundService(
                this,
                2,
                Intent(this, TelosAdbPairingService::class.java)
                    .setAction(ACTION_REPLY)
                    .putExtra(EXTRA_HOST, host)
                    .putExtra(EXTRA_PORT, port),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        } else {
            PendingIntent.getService(
                this,
                2,
                Intent(this, TelosAdbPairingService::class.java)
                    .setAction(ACTION_REPLY)
                    .putExtra(EXTRA_HOST, host)
                    .putExtra(EXTRA_PORT, port),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        }
        val remote = RemoteInput.Builder(KEY_CODE)
            .setLabel(getString(I18nR.string.telos_shizuku_pair_code))
            .build()
        val action = NotificationCompat.Action.Builder(
            0,
            getString(I18nR.string.telos_shizuku_pair_notif_enter),
            reply,
        ).addRemoteInput(remote)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(false)
            .build()
        return base()
            .setContentTitle(getString(I18nR.string.telos_shizuku_pair_notif_found))
            .setContentText(getString(I18nR.string.telos_shizuku_pair_found, host, port))
            .setOnlyAlertOnce(false)
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
        private const val EXTRA_AUTOMATIC = "automatic"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"

        fun startIntent(context: Context, automatic: Boolean = true) =
            Intent(context, TelosAdbPairingService::class.java).setAction(ACTION_START).putExtra(EXTRA_AUTOMATIC, automatic)

        fun stopIntent(context: Context) =
            Intent(context, TelosAdbPairingService::class.java).setAction(ACTION_STOP)

        fun start(context: Context, automatic: Boolean = true) {
            val app = context.applicationContext
            val i = startIntent(app, automatic)
            TelosShizukuHost.notifyPairingSearch(automatic)
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i) else app.startService(i)
            // Launch while the initiating Activity is visible, before background-start limits apply.
            openWirelessDebugging(context)
        }

        fun openWirelessDebugging(context: Context) {
            val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            TelosShizukuHost.requestWirelessNavigation()
            val tries = listOf(
                Intent().setClassName("com.android.settings", "com.android.settings.development.WirelessDebuggingActivity").addFlags(flags),
                Intent().setClassName("com.android.settings", "com.android.settings.Settings\$DevelopmentSettingsDashboardActivity")
                    .putExtra(":settings:fragment_args_key", "toggle_adb_wireless").addFlags(flags),
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .putExtra(":settings:fragment_args_key", "toggle_adb_wireless").addFlags(flags),
            )
            for (intent in tries) {
                if (runCatching { context.startActivity(intent) }.isSuccess) return
            }
        }
    }
}
