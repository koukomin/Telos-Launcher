package de.mm20.launcher2.network.api

import kotlinx.coroutines.flow.StateFlow

/** Where a local app can reach a WireGuard config as an HTTP proxy (CONNECT and plain HTTP, TCP only). */
data class LocalProxyEndpoint(val host: String, val port: Int)

/** Why no endpoint is available although a config was selected. */
enum class LocalProxyBlock {
    /** Telos Network (the VPN / Go engine) is not running. */
    VpnOff,

    /** The selected config does not exist (any more). */
    ConfigMissing,

    /** The selected config is switched off. */
    ConfigDisabled,

    /** The tunnel of the config is not up (connecting, no handshake, error). */
    TunnelDown,

    /** The engine refused to start the local proxy server. */
    EngineError,
}

sealed interface LocalProxyState {
    /** No config selected, no server is running. */
    data object Off : LocalProxyState

    /** The server listens on [endpoint] and every connection that enters it leaves through the tunnel of [configId]. */
    data class Ready(val configId: Int, val endpoint: LocalProxyEndpoint) : LocalProxyState

    /** A config is selected but the server is not running. Apps must not connect directly. */
    data class Blocked(val reason: LocalProxyBlock) : LocalProxyState
}

/**
 * Exposes one of the user's WireGuard configs as a loopback HTTP proxy. This uses the HTTP proxy server
 * of the Go engine (firestack `Services`, type `svchttp`), bridged to the proxy `"wg" + id`. The engine
 * dials the target itself through the WireGuard tunnel, so the app that uses the proxy does not have to be
 * routed through the VPN. The server only runs while the VPN is on and the tunnel is up; otherwise
 * [state] is [LocalProxyState.Blocked] and nothing is listening (fail closed).
 */
interface WireguardLocalProxy : EngineComponent {
    val state: StateFlow<LocalProxyState>

    /** Selects the config to expose. `null` (or an id <= 0) switches the server off. */
    fun select(configId: Int?)
}
