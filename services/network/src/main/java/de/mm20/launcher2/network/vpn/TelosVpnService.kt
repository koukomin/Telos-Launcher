package de.mm20.launcher2.network.vpn

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.intra.DefaultDNS
import com.celzero.firestack.intra.Intra
import com.celzero.firestack.intra.Tunnel
import com.celzero.firestack.settings.Settings
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.StopReason
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.BlocklistController
import de.mm20.launcher2.network.api.ConnectionType
import de.mm20.launcher2.network.api.DnsController
import de.mm20.launcher2.network.api.FirewallController
import de.mm20.launcher2.network.api.FlowEnvironment
import de.mm20.launcher2.network.api.IpMode
import de.mm20.launcher2.network.api.LogController
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.api.NotificationDetail
import de.mm20.launcher2.network.api.WireguardController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Which IP families and which MTU the interface was built with. */
private data class Routes(val has4: Boolean, val has6: Boolean, val mtu: Int)

/**
 * The VPN. It builds the virtual interface, hands its file descriptor to the Go engine (firestack)
 * and keeps both in step with the networks of the device.
 *
 * Lifecycle and safety:
 *  - Only [NetworkEngine.start], the boot receiver, or the system's always-on setting start it.
 *    It returns START_NOT_STICKY, so Android never restarts it behind the user's back.
 *  - [shutdown] is the single way out. It closes the interface first, so that traffic flows
 *    normally again, whatever else fails afterwards. If the Go engine hangs while closing, the
 *    process is killed as a last resort, because a dead process cannot keep a VPN up.
 *  - Failure to start, a tunnel that went away, too many internal errors, and the user revoking
 *    the VPN all lead to [shutdown].
 *
 * Adapted from Rethink DNS' `BraveVPNService` (Apache-2.0), reduced to the core.
 */
class TelosVpnService : VpnService(), KoinComponent {
    private val engine: NetworkEngine by inject()
    private val settings: NetworkSettings by inject()
    private val firewall: FirewallController by inject()
    private val wireguard: WireguardController by inject()
    private val dns: DnsController by inject()
    private val blocklists: BlocklistController by inject()
    private val logs: LogController by inject()
    private val apps: AppDirectory by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ops = Dispatchers.Default.limitedParallelism(1)

    private enum class Phase { Idle, Starting, Running, Stopping }

    private val lock = Any()
    private var phase = Phase.Idle

    // all of the following are only touched on `ops` (or under `lock` during shutdown)
    @Volatile
    private var pfd: ParcelFileDescriptor? = null

    @Volatile
    private var tunnel: Tunnel? = null
    private var monitor: NetworkMonitor? = null
    private var routes: Routes? = null
    private var healthJob: Job? = null
    private var counterJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        current = this
        VpnNotifications.ensureChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == SERVICE_INTERFACE) {
            // The user selected Telos Network as always-on VPN in the Android settings. That is an
            // explicit user action, so honour it.
            try {
                ContextCompat.startForegroundService(this, Intent(this, TelosVpnService::class.java).setAction(ACTION_START))
            } catch (e: Exception) {
                // not allowed right now; the user can start it from the app
            }
        }
        return super.onBind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                settings.update { it.copy(engineWanted = false) }
                settings.flush()
                requestStop(StopReason.User, null)
            }
            ACTION_START -> begin()
            else -> {
                // null or unknown intents (a restart by the system) must never switch the VPN on
                if (synchronized(lock) { phase == Phase.Idle }) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        // Every start through startForegroundService must be answered with startForeground
        if (!enterForeground()) {
            requestStop(StopReason.Failure, "foreground service")
            return
        }
        synchronized(lock) {
            if (phase != Phase.Idle) return
            phase = Phase.Starting
        }
        scope.launch(ops) { startTunnel() }
    }

    private fun enterForeground(): Boolean {
        val notification = VpnNotifications.service(this, settings.current, logs.stats.value.connectionsBlocked)
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            listOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            listOf(0)
        }
        for (type in types) {
            try {
                if (type == 0) {
                    startForeground(VpnNotifications.ID_SERVICE, notification)
                } else {
                    ServiceCompat.startForeground(this, VpnNotifications.ID_SERVICE, notification, type)
                }
                return true
            } catch (e: Exception) {
                // try the next type
            }
        }
        return false
    }

    // --- starting ---

    private suspend fun startTunnel() {
        // what this call created itself, so it can be undone even if a shutdown raced with the start
        var createdFd: ParcelFileDescriptor? = null
        var createdTunnel: Tunnel? = null
        try {
            val mon = NetworkMonitor(this)
            monitor = mon
            mon.start()
            // Give Android a moment to report the networks; without any, the tunnel is still built
            withTimeoutOrNull(NETWORK_WAIT_MS) { mon.snapshot.first { it.networks.isNotEmpty() } }
            val snapshot = mon.snapshot.value

            val r = computeRoutes(snapshot)
            val fd = establish(r, snapshot) ?: throw IllegalStateException(getString(I18nR.string.net_error_permission))
            createdFd = fd
            pfd = fd
            routes = r
            checkNotStopping()

            // The engine keeps its own copy of the descriptor, and this service the original,
            // so that closing it here is always enough to take the interface down.
            Settings.dupTunFd(true)
            val bridge = FlowBridge(
                connectivity = getSystemService(ConnectivityManager::class.java),
                environment = { environmentNow() },
                protectFd = { protect(it) },
                networkFor = { v6 -> networkFor(v6) },
                firewall = firewall,
                wireguard = wireguard,
                dns = dns,
                blocklists = blocklists,
                logs = logs,
                settings = settings,
                apps = apps,
                onFault = { why -> requestStop(StopReason.Failure, why) },
            )
            val t = Intra.connect(
                fd.fd.toLong(),
                r.mtu.toLong(),
                r.mtu.toLong(),
                TunnelConstants.ADDRESSES,
                TunnelConstants.FAKE_DNS,
                newBootstrapDns(),
                bridge,
            )
            createdTunnel = t
            tunnel = t
            checkNotStopping()

            // Telos Network always filters and resolves: DNS through the tunnel's resolver, every
            // flow offered to the bridge. Android 8/9 cannot tell the owner of a connection, so the
            // engine reads it from /proc there.
            Settings.setTunMode(
                Settings.DNSModeIP,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.BlockModeFilter else Settings.BlockModeFilterProc,
                Settings.PtModeAuto,
            )
            applyNetworks(t, snapshot)
            engine.applyComponents(t)

            synchronized(lock) {
                if (phase != Phase.Starting) throw StoppedWhileStarting()
                phase = Phase.Running
            }
            engine.onServiceReady(t)
            startHealthCheck(t)
            startCounterUpdates()

            // From now on follow the networks. The first value is compared with the one the
            // tunnel was built with, so changes during startup are not lost.
            scope.launch(ops) {
                var last = snapshot
                mon.snapshot.collect { s ->
                    if (s != last) {
                        last = s
                        onNetworksChanged(s)
                    }
                }
            }
        } catch (e: Throwable) {
            if (isStopping()) {
                // a shutdown is already running; just undo what this start created
                createdTunnel?.let { runCatching { it.disconnect() } }
                createdFd?.let { runCatching { it.close() } }
            } else {
                requestStop(StopReason.Failure, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private class StoppedWhileStarting : Exception()

    private fun isStopping() = synchronized(lock) { phase == Phase.Stopping }

    private fun checkNotStopping() {
        if (isStopping()) throw StoppedWhileStarting()
    }

    private fun newBootstrapDns(): DefaultDNS? = try {
        Intra.newDefaultDNS(Backend.DNS53, TunnelConstants.BOOTSTRAP_DNS, "")
    } catch (e: Exception) {
        null
    }

    private fun computeRoutes(snapshot: NetSnapshot): Routes {
        val mode = settings.current.ipMode
        val has4: Boolean
        val has6: Boolean
        when (mode) {
            IpMode.V4 -> { has4 = true; has6 = false }
            IpMode.V6 -> { has4 = false; has6 = true }
            IpMode.V46 -> {
                // IPv4 stays routed on IPv6-only networks (NAT64 / 464XLAT); IPv6 follows the network.
                // With no network known at all, both are routed so traffic resumes when one appears.
                has4 = true
                has6 = snapshot.hasV6 || snapshot.networks.isEmpty()
            }
        }
        val configured = settings.current.mtu
        val mtu = when {
            configured > 0 -> configured
            snapshot.minMtu > 0 -> snapshot.minMtu
            else -> TunnelConstants.DEFAULT_MTU
        }.coerceIn(TunnelConstants.MIN_MTU, MAX_MTU)
        return Routes(has4, has6, mtu)
    }

    /** Builds the interface. Returns null when Android refuses (the VPN permission is gone). */
    private fun establish(r: Routes, snapshot: NetSnapshot): ParcelFileDescriptor? {
        val s = settings.current
        val b = Builder()
            .setSession("Telos Network")
            .setMtu(r.mtu)
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            b.setConfigureIntent(
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }
        underlyingNetworks(snapshot)?.let { b.setUnderlyingNetworks(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) b.setMetered(s.vpnMetered)
        if (s.allowBypass) b.allowBypass()

        if (s.excludeSelf) excludeApp(b, packageName)
        firewall.appRules.value.values.filter { it.excludeFromVpn }.forEach { rule ->
            apps.apps.value.firstOrNull { it.appId == rule.appId }?.packages?.forEach { excludeApp(b, it) }
        }

        val noRoutes = !r.has4 && !r.has6
        val v4 = r.has4 || noRoutes
        if (v4) b.addAddress(TunnelConstants.GATEWAY_V4, TunnelConstants.PREFIX_V4)
        if (r.has6) b.addAddress(TunnelConstants.GATEWAY_V6, TunnelConstants.PREFIX_V6)

        // the fake DNS servers, reachable through the tunnel only
        if (v4) {
            b.addDnsServer(TunnelConstants.DNS_V4)
            b.addRoute(TunnelConstants.DNS_V4, 32)
        }
        if (r.has6) {
            b.addDnsServer(TunnelConstants.DNS_V6)
            b.addRoute(TunnelConstants.DNS_V6, 128)
        }

        // everything else that has to be seen by the firewall
        if (v4) {
            if (s.routeLan) b.addRoute("0.0.0.0", 0) else RouteTable.ipv4WithoutLan().forEach { (a, p) -> b.addRoute(a, p) }
        }
        if (r.has6) {
            if (s.routeLan) b.addRoute("::", 0) else RouteTable.ipv6WithoutLan.forEach { (a, p) -> b.addRoute(a, p) }
        }
        return b.establish()
    }

    private fun excludeApp(b: Builder, packageName: String) {
        try {
            b.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            // the package is gone
        }
    }

    private fun underlyingNetworks(snapshot: NetSnapshot): Array<Network>? =
        snapshot.networks.map { it.network }.takeIf { it.isNotEmpty() }?.toTypedArray()

    private fun networkFor(ipv6: Boolean): Network? {
        val s = monitor?.snapshot?.value ?: return null
        val best = if (ipv6) s.networks.firstOrNull { it.hasV6 } else s.networks.firstOrNull { it.hasV4 }
        return (best ?: s.active)?.network
    }

    private fun environmentNow(): FlowEnvironment {
        val active = monitor?.snapshot?.value?.active
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        return FlowEnvironment(
            network = active?.type ?: ConnectionType.None,
            roaming = active != null && active.roaming && active.type == ConnectionType.Mobile,
            metered = active?.metered == true,
            screenOn = power?.isInteractive != false,
            deviceLocked = keyguard?.isKeyguardLocked == true,
            appInForeground = null,
        )
    }

    // --- following the networks ---

    private fun applyNetworks(t: Tunnel, snapshot: NetSnapshot) {
        underlyingNetworks(snapshot)?.let { setUnderlyingNetworks(it) }
        val servers = snapshot.dnsServers.ifEmpty { TunnelConstants.BOOTSTRAP_DNS.split(',') }
        try {
            Intra.setSystemDNS(t, servers.joinToString(","))
        } catch (e: Exception) {
            // keeps the previous system DNS
        }
    }

    private suspend fun onNetworksChanged(snapshot: NetSnapshot) {
        val t = tunnel ?: return
        if (synchronized(lock) { phase != Phase.Running }) return
        try {
            applyNetworks(t, snapshot)
            val next = computeRoutes(snapshot)
            if (next != routes) {
                // seamless hand-over: the new interface replaces the old one without a gap
                val fd = establish(next, snapshot) ?: throw IllegalStateException("VPN permission was revoked")
                val proto = when {
                    next.has4 && next.has6 -> Settings.Ns46
                    next.has6 -> Settings.Ns6
                    else -> Settings.Ns4
                }
                t.setLinkAndRoutes2(fd.fd.toLong(), next.mtu.toLong(), next.mtu.toLong(), proto)
                val old = pfd
                pfd = fd
                routes = next
                try {
                    old?.close()
                } catch (e: Exception) {
                    // already closed
                }
            }
            try {
                t.getProxies().refreshProxies()
                t.getResolver().refresh()
            } catch (e: Exception) {
                // refreshing is best effort
            }
        } catch (e: Throwable) {
            requestStop(StopReason.Failure, e.message ?: e.javaClass.simpleName)
        }
    }

    // --- watching ---

    /** A tunnel the engine closed by itself (it does that on fatal errors) must not leave a dead VPN behind. */
    private fun startHealthCheck(t: Tunnel) {
        healthJob?.cancel()
        healthJob = scope.launch {
            var misses = 0
            while (isActive) {
                delay(HEALTH_INTERVAL_MS)
                val alive = try {
                    t.isConnected()
                } catch (e: Throwable) {
                    false
                }
                if (alive) {
                    misses = 0
                } else if (++misses >= HEALTH_MISSES) {
                    requestStop(StopReason.Failure, "tunnel closed")
                    return@launch
                }
            }
        }
    }

    private fun startCounterUpdates() {
        counterJob?.cancel()
        if (settings.current.notificationDetail != NotificationDetail.WithCounters) return
        counterJob = scope.launch {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            while (isActive) {
                delay(COUNTER_INTERVAL_MS)
                try {
                    nm.notify(
                        VpnNotifications.ID_SERVICE,
                        VpnNotifications.service(this@TelosVpnService, settings.current, logs.stats.value.connectionsBlocked),
                    )
                } catch (e: Exception) {
                    // not allowed to notify
                }
            }
        }
    }

    // --- stopping ---

    /** Stops the VPN. Safe to call from any thread, any number of times. */
    internal fun requestStop(reason: StopReason, detail: String?) {
        synchronized(lock) {
            if (phase == Phase.Stopping) return
            phase = Phase.Stopping
        }
        // A thread of its own: the Go engine may block while closing, and nothing may wait for it
        Thread({ shutdown(reason, detail) }, "telos-network-stop").start()
    }

    private fun shutdown(reason: StopReason, detail: String?) {
        healthJob?.cancel()
        counterJob?.cancel()
        monitor?.stop()
        val t = tunnel
        tunnel = null

        // Closing the engine's tunnel releases its copy of the descriptor. If it hangs, kill the
        // process: the VPN is gone with it and the internet works again.
        if (t != null) {
            val closer = Thread({
                try {
                    t.disconnect()
                } catch (e: Throwable) {
                    // closing failed; the interface is closed below anyway
                }
            }, "telos-network-disconnect")
            closer.start()
            closer.join(DISCONNECT_TIMEOUT_MS)
            if (closer.isAlive) {
                closePfd()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        closePfd()

        engine.onServiceStopped(reason, detail)
        if (reason != StopReason.User && settings.current.notifyOnFailure) {
            VpnNotifications.alert(this, engine.alertTextFor(reason))
        }
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            // not in the foreground
        }
        synchronized(lock) { phase = Phase.Idle }
        stopSelf()
    }

    private fun closePfd() {
        val fd = pfd
        pfd = null
        try {
            fd?.close()
        } catch (e: Exception) {
            // already closed
        }
    }

    /** Android or another VPN app took the VPN away. */
    override fun onRevoke() {
        requestStop(StopReason.Revoked, null)
        super.onRevoke()
    }

    override fun onDestroy() {
        if (current === this) current = null
        val running = synchronized(lock) { phase != Phase.Idle && phase != Phase.Stopping }
        if (running) {
            // destroyed without a shutdown (for example by the system): clean up on this thread
            tunnel?.let { runCatching { it.disconnect() } }
            tunnel = null
            closePfd()
            monitor?.stop()
            engine.onServiceStopped(StopReason.Failure, null)
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "de.mm20.launcher2.network.START"
        const val ACTION_STOP = "de.mm20.launcher2.network.STOP"

        private const val NETWORK_WAIT_MS = 2_000L
        private const val HEALTH_INTERVAL_MS = 10_000L
        private const val HEALTH_MISSES = 2
        private const val COUNTER_INTERVAL_MS = 5_000L
        private const val DISCONNECT_TIMEOUT_MS = 5_000L
        private const val MAX_MTU = 9000

        /** The running instance, if any. */
        @Volatile
        internal var current: TelosVpnService? = null
            private set
    }
}
