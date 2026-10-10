package de.mm20.launcher2.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.downloads.TorrentRoute
import de.mm20.launcher2.network.api.LocalProxyBlock
import de.mm20.launcher2.network.api.WireguardController
import de.mm20.launcher2.ui.R
import org.koin.compose.koinInject

/** One line that tells how torrents connect when a Telos Network WireGuard proxy is chosen; null for other routes */
@Composable
internal fun torrentWgStatusText(route: TorrentRoute): String? {
    val wg: WireguardController = koinInject()
    val configs by wg.configs.collectAsState()
    return when (route) {
        is TorrentRoute.Wireguard -> stringResource(
            R.string.au5_wgtorrent_status_ready,
            configs.firstOrNull { it.id == route.configId }?.name ?: stringResource(R.string.au5_wgtorrent_removed),
        )
        is TorrentRoute.WireguardBlocked -> stringResource(
            when (route.reason) {
                LocalProxyBlock.VpnOff -> R.string.au5_wgtorrent_blocked_vpn_off
                LocalProxyBlock.ConfigMissing -> R.string.au5_wgtorrent_blocked_missing
                LocalProxyBlock.ConfigDisabled -> R.string.au5_wgtorrent_blocked_disabled
                LocalProxyBlock.TunnelDown -> R.string.au5_wgtorrent_blocked_down
                LocalProxyBlock.EngineError -> R.string.au5_wgtorrent_blocked_error
            }
        )
        else -> null
    }
}
