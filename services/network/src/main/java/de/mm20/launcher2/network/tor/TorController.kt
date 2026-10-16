package de.mm20.launcher2.network.tor

import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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
    private val mutableProgress = MutableStateFlow(0)
    val progress: StateFlow<Int> = mutableProgress
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError
    private var nextStartAt = 0L
    @Volatile internal var ready = false
        private set
    @Volatile private var requested = false
    private var tunnel: Tunnel? = null
    private var service: TorService? = null
    private var bound = false
    private var monitor: Job? = null
    private var dnsPort = 0
    private var registeredPort = 0
    private var observingErrors = false
    private val errorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != TorService.ACTION_ERROR ||
                intent.getStringExtra(TorService.EXTRA_SERVICE_PACKAGE_NAME) != context.packageName) return
            unavailable(IllegalStateException(intent.getStringExtra(Intent.EXTRA_TEXT) ?: TorStatus.Unavailable.name))
            // A bound service stays alive after stopSelf(), even if its native thread has failed.
            // Release it before the monitor's next bounded retry.
            if (bound) {
                bound = false
                runCatching { context.unbindService(connection) }
            }
            service = null
            registeredPort = 0
        }
    }
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
        requested = settings.current.torWasActive
        if (!settings.current.torEnabled) return@withContext
        ContextCompat.registerReceiver(context, errorReceiver, IntentFilter(TorService.ACTION_ERROR), ContextCompat.RECEIVER_NOT_EXPORTED)
        observingErrors = true
        mutableStatus.value = TorStatus.Waiting
        monitor = scope.launch {
            while (isActive) {
                val config = settings.current
                if (!config.torEnabled) {
                    unavailable()
                } else if (requested || !config.torAutoStart || config.torAppIds.isNotEmpty()) {
                    if (!bound && android.os.SystemClock.elapsedRealtime() >= nextStartAt) startService()
                    val tor = service
                    val port = tor?.socksPort ?: 0
                    val phase = try {
                        withContext(Dispatchers.IO) { tor?.getInfo("status/bootstrap-phase") }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) { unavailable(error); null }
                    mutableProgress.value = TorPolicy.bootstrapProgress(phase)
                    val bootstrapped = mutableProgress.value == 100
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
                        } catch (error: Exception) { unavailable(error) }
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
            nextStartAt = android.os.SystemClock.elapsedRealtime() + 30_000L
            mutableError.value = null
            mutableStatus.value = TorStatus.Starting
            settings.update { it.copy(torWasActive = true) }
            settings.flush()
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
        } catch (error: Exception) { unavailable(error) }
        catch (error: LinkageError) { unavailable(error) }
    }

    private fun unavailable(error: Throwable? = null) {
        ready = false
        if (error != null) mutableError.value = error.message ?: error.javaClass.simpleName
        mutableStatus.value = TorStatus.Unavailable
        try { tunnel?.closeConns("") } catch (_: Exception) { }
    }

    override suspend fun onTunnelDisconnected() = withContext(Dispatchers.Main.immediate) { shutdown() }

    private fun shutdown() {
        ready = false
        monitor?.cancel()
        monitor = null
        if (observingErrors) runCatching { context.unregisterReceiver(errorReceiver) }
        observingErrors = false
        if (bound) try { context.unbindService(connection) } catch (_: Exception) { }
        bound = false
        service = null
        tunnel = null
        registeredPort = 0
        nextStartAt = 0
        mutableProgress.value = 0
        mutableError.value = null
        mutableStatus.value = TorStatus.Off
    }

    private fun freePort() = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
}
