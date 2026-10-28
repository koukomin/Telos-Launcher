package de.mm20.launcher2.network.impl.wg

import de.mm20.launcher2.network.api.*

/** The pinned native engine supports two WireGuard hops, with a full-route entry tunnel. */
internal object WgHopPolicy {
    fun validate(config: WireguardConfig, configs: List<WireguardConfig>) {
        val viaId = config.hopVia ?: return
        val via = configs.firstOrNull { it.id == viaId }
        if (via == null || viaId == config.id || via.hopVia != null || configs.any { it.id != config.id && it.hopVia == config.id }) {
            throw WgConfigException(WgConfigError.InvalidHop)
        }
        val routes = via.peers.flatMap { it.allowedIps }.toSet()
        if ("0.0.0.0/0" !in routes || "::/0" !in routes) throw WgConfigException(WgConfigError.InvalidHop)
    }
}
