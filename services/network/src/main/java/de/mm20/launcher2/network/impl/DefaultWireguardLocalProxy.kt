package de.mm20.launcher2.network.impl

import android.util.Log
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.intra.Tunnel
import de.mm20.launcher2.network.api.LocalProxyBlock
import de.mm20.launcher2.network.api.LocalProxyEndpoint
import de.mm20.launcher2.network.api.LocalProxyState
import de.mm20.launcher2.network.api.WgStatus
import de.mm20.launcher2.network.api.WireguardController
import de.mm20.launcher2.network.api.WireguardLocalProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Runs the `svchttp` server of the Go engine on a random loopback port and bridges it to the WireGuard
 * proxy of the selected config. See `firestack/intra/rnet/servers.go`: `AddServer` stores the server and then
 * tries to bridge it to a proxy with the same id, which fails ("no such proxy") and is reported as an error
 * although the server exists; the real bridge is done afterwards with `bridge(serverId, proxyId)`.
 * The server is started only after `type() == pxhttp` confirmed that it is bridged, so it can never dial directly.
 */
internal class DefaultWireguardLocalProxy(
    private val wireguard: WireguardController,
) : WireguardLocalProxy {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tunnel = MutableStateFlow<Tunnel?>(null)
    private val selected = MutableStateFlow<Int?>(null)
    private val _state = MutableStateFlow<LocalProxyState>(LocalProxyState.Off)
    override val state: StateFlow<LocalProxyState> = _state

    /** What is running now; only touched from the single collector below. */
    private var runningTunnel: Tunnel? = null
    private var runningConfig: Int? = null

    init {
        scope.launch {
            combine(tunnel, selected, wireguard.configs, wireguard.status) { t, sel, configs, status ->
                Snapshot(t, sel, configs.firstOrNull { it.id == sel }, sel?.let { status[it] })
            }.collectLatest { reconcile(it) }
        }
    }

    private class Snapshot(
        val tunnel: Tunnel?,
        val selected: Int?,
        val config: de.mm20.launcher2.network.api.WireguardConfig?,
        val status: WgStatus?,
    )

    override fun select(configId: Int?) {
        selected.value = configId?.takeIf { it > 0 }
    }

    override suspend fun onTunnelConnected(tunnel: Tunnel) {
        // a new tunnel has new servers; the old server (if any) died with the old tunnel
        if (runningTunnel !== tunnel) {
            runningTunnel = null
            runningConfig = null
        }
        this.tunnel.value = tunnel
    }

    override suspend fun onTunnelDisconnected() {
        tunnel.value = null
    }

    private fun reconcile(s: Snapshot) {
        val sel = s.selected
        if (sel == null) {
            stop()
            _state.value = LocalProxyState.Off
            return
        }
        val t = s.tunnel
        val block = when {
            t == null -> LocalProxyBlock.VpnOff
            s.config == null -> LocalProxyBlock.ConfigMissing
            !s.config.enabled -> LocalProxyBlock.ConfigDisabled
            s.status != WgStatus.Up -> LocalProxyBlock.TunnelDown
            else -> null
        }
        if (block != null || t == null || s.config == null) {
            if (t != null) stop(t) else forget()
            _state.value = LocalProxyState.Blocked(block ?: LocalProxyBlock.VpnOff)
            return
        }
        val current = _state.value
        if (runningTunnel === t && runningConfig == sel && current is LocalProxyState.Ready && current.configId == sel) return
        stop(t)
        _state.value = try {
            LocalProxyState.Ready(sel, start(t, s.config.proxyId).also { runningTunnel = t; runningConfig = sel })
        } catch (e: Throwable) {
            Log.w(TAG, "Could not start the local proxy", e)
            stop(t)
            LocalProxyState.Blocked(LocalProxyBlock.EngineError)
        }
    }

    private fun start(t: Tunnel, proxyId: String): LocalProxyEndpoint {
        val port = ServerSocket(0, 1, InetAddress.getByName(HOST)).use { it.localPort }
        val services = t.getServices()
        // AddServer reports "no proxy named svchttp" although the server was stored: ignore that, check below
        try {
            services.addServer(Backend.SVCHTTP, "http://$HOST:$port")
        } catch (e: Exception) {
            Log.d(TAG, "addServer: ${e.message}")
        }
        val server = services.getServer(Backend.SVCHTTP)
        services.bridge(Backend.SVCHTTP, proxyId)
        // only a server that is bridged to the WireGuard proxy reports the proxied type
        if (server.type() != Backend.PXHTTP) throw IllegalStateException("The local proxy is not bridged")
        server.start()
        return LocalProxyEndpoint(HOST, port)
    }

    private fun stop(t: Tunnel? = runningTunnel) {
        try {
            t?.getServices()?.removeServer(Backend.SVCHTTP)
        } catch (e: Throwable) {
            // tunnel already gone: its servers are gone too
        }
        forget()
    }

    private fun forget() {
        runningTunnel = null
        runningConfig = null
    }

    private companion object {
        const val TAG = "WgLocalProxy"
        const val HOST = "127.0.0.1"
    }
}
