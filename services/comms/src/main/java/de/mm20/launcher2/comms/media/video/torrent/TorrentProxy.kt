package de.mm20.launcher2.comms.media.video.torrent

enum class TorrentProxyType { None, Http, Socks5 }

/** The proxy for all torrent traffic (set by Telos Downloads, applies to the shared session, so to Telos Video too) */
data class TorrentProxy(
    val type: TorrentProxyType = TorrentProxyType.None,
    val host: String = "",
    val port: Int = 0,
) {
    val enabled: Boolean get() = type != TorrentProxyType.None

    /** A proxy that is switched on needs a host and a port; without them torrents must not start */
    val valid: Boolean get() = !enabled || (host.isNotBlank() && port in 1..65535)
}
