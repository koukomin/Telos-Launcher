package de.mm20.launcher2.downloads

import android.content.Context
import de.mm20.launcher2.comms.media.video.torrent.TorrentProxy
import de.mm20.launcher2.comms.media.video.torrent.TorrentProxyType
import de.mm20.launcher2.comms.media.video.torrent.TorrentSession
import de.mm20.launcher2.network.api.LocalProxyBlock
import de.mm20.launcher2.network.api.LocalProxyState
import de.mm20.launcher2.network.api.WireguardLocalProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** What the torrents currently use to reach the internet */
sealed interface TorrentRoute {
    /** Direct connection (no proxy set) */
    data object Direct : TorrentRoute

    /** The proxy of the download settings (HTTP or SOCKS5) */
    data object CustomProxy : TorrentRoute

    /** Through the WireGuard config [configId] of Telos Network */
    data class Wireguard(val configId: Int) : TorrentRoute

    /** A WireGuard config is selected but cannot be used now: torrents wait, nothing goes out */
    data class WireguardBlocked(val configId: Int, val reason: LocalProxyBlock) : TorrentRoute
}

/**
 * Decides the proxy of the shared torrent session from the download settings and the state of the local
 * proxy of Telos Network. With a WireGuard config selected, the torrents use only its loopback proxy; if that is
 * not available the session gets an incomplete proxy (a dead end), which refuses to start and blocks a running
 * session, so there is never a direct fallback.
 */
class TorrentProxyGate(
    private val context: Context,
    private val settings: DownloadSettings,
    private val wireguard: WireguardLocalProxy,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _route = MutableStateFlow<TorrentRoute>(TorrentRoute.Direct)
    val route: StateFlow<TorrentRoute> = _route

    /** True while torrents must not run or start (a WireGuard config is selected and not usable) */
    val blocked: Boolean get() = _route.value is TorrentRoute.WireguardBlocked

    init {
        // decided before anything can start, then kept in sync; independent of the Downloads screen and manager
        applyNow()
        scope.launch {
            combine(settings.values, wireguard.state) { s, w -> compute(s, w) }.collect { (route, proxy) ->
                _route.value = route
                TorrentSession.setRoute(context, route is TorrentRoute.Wireguard || route is TorrentRoute.WireguardBlocked, proxy)
            }
        }
    }

    /** Applies the current decision to the torrent session now (before a torrent starts) and returns the route */
    fun applyNow(): TorrentRoute {
        val (route, proxy) = compute(settings.current, wireguard.state.value)
        _route.value = route
        TorrentSession.setRoute(context, route is TorrentRoute.Wireguard || route is TorrentRoute.WireguardBlocked, proxy)
        return route
    }

    private fun compute(s: DownloadSettingsValues, w: LocalProxyState): Pair<TorrentRoute, TorrentProxy> {
        val id = s.torrentWgConfigId
        if (id <= 0) {
            wireguard.select(null)
            return (if (s.proxyType == ProxyType.None) TorrentRoute.Direct else TorrentRoute.CustomProxy) to s.torrentProxy()
        }
        wireguard.select(id)
        // the state may still describe the previous selection for a moment
        val ready = (w as? LocalProxyState.Ready)?.takeIf { it.configId == id }
        if (ready != null) {
            return TorrentRoute.Wireguard(id) to TorrentProxy(TorrentProxyType.Http, ready.endpoint.host, ready.endpoint.port)
        }
        val reason = (w as? LocalProxyState.Blocked)?.reason ?: LocalProxyBlock.TunnelDown
        // Http without host and port: not valid, so the session refuses to start / is pointed at a dead end
        return TorrentRoute.WireguardBlocked(id, reason) to TorrentProxy(TorrentProxyType.Http, "", 0)
    }
}
