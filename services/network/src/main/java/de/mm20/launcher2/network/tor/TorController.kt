package de.mm20.launcher2.network.tor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.celzero.firestack.intra.Intra
import com.celzero.firestack.intra.Tunnel
import de.mm20.launcher2.network.api.EngineComponent
import de.mm20.launcher2.network.api.NetworkSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.torproject.jni.TorService
import java.net.InetAddress
import java.net.ServerSocket

enum class TorStatus { Off, Waiting, Starting, Ready, Unavailable }

/** Tor shares Telos' excluded UID; its relay sockets never loop through our VPN. */
class TorController internal constructor(private val context: Context, private val settings: NetworkSettings) : EngineComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableStatus = MutableStateFlow(TorStatus.Off)
    val status: StateFlow<TorStatus> = mutableStatus
    @Volatile internal var ready = false
        private set
    @Volatile private var requested = false
    private var tunnel: Tunnel? = null
    private var service: TorService? = null
    private var bound = false
    private var monitor: Job? = null
    private var dnsPort = 0
    private var registeredPort = 0
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? TorService.LocalBinder)?.service
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null; unavailable() }
        override fun onBindingDied(name: ComponentName?) { service = null; unavailable() }
        override fun onNullBinding(name: ComponentName?) { unavailable() }
    }

    /** Called without blocking a DNS callback. The first request is blocked; the app may retry. */
    internal fun requestForOnion() {
        if (settings.current.torEnabled && settings.current.torAutoStart) requested = true
    }

    override suspend fun onTunnelConnected(tunnel: Tunnel) = withContext(Dispatchers.Main.immediate) {
        shutdown()
        this@TorController.tunnel = tunnel
        requested = false
        if (!settings.current.torEnabled) return@withContext
        mutableStatus.value = TorStatus.Waiting
        monitor = scope.launch {
            while (isActive) {
                val config = settings.current
                if (!config.torEnabled) {
                    unavailable()
                } else if (requested || !config.torAutoStart || config.torAppIds.isNotEmpty()) {
                    if (!bound) startService()
                    val tor = service
                    val port = tor?.socksPort ?: 0
                    val bootstrapped = withContext(Dispatchers.IO) {
                        tor?.getInfo("status/bootstrap-phase")?.contains("PROGRESS=100 ") == true
                    }
                    if (port > 0 && bootstrapped) {
                        try {
                            if (registeredPort != port) {
                                withContext(Dispatchers.IO) {
                                    tunnel.proxies.addProxy(TorPolicy.PROXY, "socks5://127.0.0.1:$port")
                                    Intra.addDNSProxy(tunnel, TorPolicy.DNS, "127.0.0.1:$dnsPort")
                                }
                                registeredPort = port
                            }
                            ready = true
                            mutableStatus.value = TorStatus.Ready
                        } catch (_: Exception) { unavailable() }
                    } else {
                        if (ready) unavailable()
                    }
                }
                delay(1000)
            }
        }
    }

    private fun startService() {
        try {
            mutableStatus.value = TorStatus.Starting
            dnsPort = freePort()
            val socks = freePort()
            // TorService supplies its private control socket and cookie authentication separately.
            TorService.getTorrc(context).writeText(
                "SocksPort 127.0.0.1:$socks\nDNSPort 127.0.0.1:$dnsPort\nHTTPTunnelPort 0\n" +
                    "AutomapHostsOnResolve 1\nAutomapHostsSuffixes .onion\n" +
                    "VirtualAddrNetworkIPv4 ${TorPolicy.VIRTUAL_V4}\nVirtualAddrNetworkIPv6 ${TorPolicy.VIRTUAL_V6}\n"
            )
            bound = context.bindService(Intent(context, TorService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound) unavailable()
        } catch (_: Exception) { unavailable() }
    }

    private fun unavailable() {
        ready = false
        mutableStatus.value = TorStatus.Unavailable
        try { tunnel?.closeConns("") } catch (_: Exception) { }
    }

    override suspend fun onTunnelDisconnected() = withContext(Dispatchers.Main.immediate) { shutdown() }

    private fun shutdown() {
        ready = false
        monitor?.cancel()
        monitor = null
        if (bound) try { context.unbindService(connection) } catch (_: Exception) { }
        bound = false
        service = null
        tunnel = null
        registeredPort = 0
        mutableStatus.value = TorStatus.Off
    }

    private fun freePort() = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
}
