package de.mm20.launcher2.network.privatedns
import de.mm20.launcher2.i18n.R as I18nR

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.api.PrivateDnsMode
import de.mm20.launcher2.network.api.PrivateDnsState
import de.mm20.launcher2.network.api.WifiDnsRule
import de.mm20.launcher2.network.api.WifiDnsRules
import de.mm20.launcher2.search.VirtualAppAppearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.collect
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class WifiDnsAutomationService : Service(), KoinComponent {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val evaluationMutex = Mutex()
    private lateinit var settings: NetworkSettings
    private lateinit var privateDns: PrivateDnsController
    private var connectivityManager: ConnectivityManager? = null
    private var callbackRegistered = false
    private var settingsJob: Job? = null
    @Volatile private var networkStateReady = false
    @Volatile private var connectedToWifi = false
    @Volatile private var currentSsid: String? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            networkStateReady = true
            connectedToWifi = true
            updateSsid(readSsid(network))
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
            networkStateReady = true
            connectedToWifi = true
            updateSsid(readSsid(caps))
        }

        override fun onLost(network: Network) {
            val cm = connectivityManager ?: return
            val stillConnected = cm.allNetworks.any {
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            connectedToWifi = stillConnected
            networkStateReady = true
            if (!stillConnected) updateSsid(null)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        settings = get()
        privateDns = get()
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        registerNetworkCallback()
        settingsJob = serviceScope.launch {
            settings.values.collect { evaluate(currentSsid, connectedToWifi) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                evaluationMutex.withLock {
                    settings.update { it.copy(wifiDnsAutomationEnabled = false) }
                    settings.flush()
                    restoreBaseline()
                }
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        if (!settings.current.wifiDnsAutomationEnabled) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (callbackRegistered) {
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
            callbackRegistered = false
        }
        settingsJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        try {
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build(),
                networkCallback,
            )
            callbackRegistered = true
            cm.allNetworks.forEach { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@forEach
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    connectedToWifi = true
                    updateSsid(readSsid(caps))
                }
            }
        } catch (_: SecurityException) {
            connectedToWifi = false
            networkStateReady = true
            updateSsid(null)
        }
        networkStateReady = true
    }

    private fun updateSsid(ssid: String?) {
        currentSsid = ssid
        networkStateReady = true
        serviceScope.launch { evaluate(ssid, connectedToWifi) }
    }

    private suspend fun evaluate(ssid: String?, wifiConnected: Boolean) = evaluationMutex.withLock {
        if (!settings.current.wifiDnsAutomationEnabled) return@withLock
        if (!networkStateReady) return@withLock
        if (!hasRequiredPermissions()) {
            settings.update { it.copy(wifiDnsAutomationEnabled = false) }
            settings.flush()
            restoreBaseline()
            stopSelf()
            return@withLock
        }
        if (wifiConnected && WifiDnsRules.normalizeSsid(ssid) == null) return@withLock
        val state = settings.current
        val rule = WifiDnsRules.matching(state.wifiDnsRules, ssid)
        if (rule == null) {
            restoreBaseline()
            return@withLock
        }

        var baseline = state.wifiDnsBaseline
        if (baseline == null) {
            baseline = (privateDns.observe() as? PrivateDnsResult.Success)?.state ?: return@withLock
            settings.update { it.copy(wifiDnsBaseline = baseline) }
            settings.flush()
        }

        val target = rule.toPrivateDnsState() ?: return@withLock
        if (state.wifiDnsLastApplied == target) return@withLock
        val result = apply(target)
        if (result is PrivateDnsResult.Success) {
            settings.update { it.copy(wifiDnsLastApplied = target) }
            settings.flush()
        }
    }

    private suspend fun restoreBaseline() {
        val state = settings.current
        val baseline = state.wifiDnsBaseline ?: return
        val lastApplied = state.wifiDnsLastApplied
        if (lastApplied != null) {
            val observed = privateDns.observe()
            if (observed !is PrivateDnsResult.Success) return
            if (observed.state == lastApplied && apply(baseline) !is PrivateDnsResult.Success) return
        }
        settings.update { it.copy(wifiDnsBaseline = null, wifiDnsLastApplied = null) }
        settings.flush()
    }

    private suspend fun apply(state: PrivateDnsState): PrivateDnsResult = when (state.mode) {
        PrivateDnsMode.AUTOMATIC, PrivateDnsMode.OFF -> privateDns.setMode(state.mode)
        PrivateDnsMode.PROVIDER -> privateDns.setProvider(state.hostname.orEmpty())
    }

    private fun WifiDnsRule.toPrivateDnsState(): PrivateDnsState? = when (mode) {
        PrivateDnsMode.AUTOMATIC, PrivateDnsMode.OFF -> PrivateDnsState(mode)
        PrivateDnsMode.PROVIDER -> PrivateDnsHost.normalize(hostname)?.let {
            PrivateDnsState(PrivateDnsMode.PROVIDER, it)
        }
    }

    private fun readSsid(network: Network): String? =
        connectivityManager?.getNetworkCapabilities(network)?.let(::readSsid)

    private fun readSsid(caps: NetworkCapabilities): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps.transportInfo as? WifiInfo
        } else {
            null
        }
        val ssid = info?.ssid ?: currentWifiSsid()
        return WifiDnsRules.normalizeSsid(ssid)
    }

    @Suppress("DEPRECATION")
    private fun currentWifiSsid(): String? =
        getSystemService(WifiManager::class.java)?.connectionInfo?.ssid

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(I18nR.string.net_channel_service),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
        }
        val openIntent = Intent().setClassName(
            packageName,
            SettingsDeepLinkContract.ACTIVITY_CLASS_NAME,
        ).putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_NETWORK)
            .setData(android.net.Uri.parse("telos://notification/network-wifi-dns"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = PendingIntent.getActivity(
            this, NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, NOTIFICATION_ID,
            Intent(this, WifiDnsAutomationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .let { VirtualAppAppearance.decorate(it, this, "telos_network_app") }
            .setContentTitle(getString(I18nR.string.net_hub_title))
            .setContentText(getString(I18nR.string.net_wifi_dns_automation))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(I18nR.string.hc_stop), stop)
        return builder.build()
    }

    companion object {
        const val ACTION_START = "de.mm20.launcher2.network.action.START_WIFI_DNS"
        const val ACTION_STOP = "de.mm20.launcher2.network.action.STOP_WIFI_DNS"
        private const val CHANNEL_ID = "telos_wifi_dns_automation"
        private const val NOTIFICATION_ID = 7111

        fun start(context: Context) {
            val intent = Intent(context, WifiDnsAutomationService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WifiDnsAutomationService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
