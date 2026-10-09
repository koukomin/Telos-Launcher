package de.mm20.launcher2.network.api

import com.celzero.firestack.intra.Tunnel

/**
 * Implemented by controllers that have to push their configuration into the Go engine
 * ([DnsController], [WireguardController], [BlocklistController]). The engine calls it every time a
 * tunnel was created, restarted or reconnected, and the controller must apply its complete state then.
 *
 * Rules for implementations:
 *  - Never throw. Catch exceptions per item so that one broken entry does not stop the rest.
 *  - Calls arrive one after another on a background thread; blocking is fine, but keep it short
 *    because the VPN is not "On" before all components finished.
 *  - When settings change at runtime, apply them yourself to the current tunnel, which you can read
 *    from [de.mm20.launcher2.network.NetworkEngine.tunnel].
 */
interface EngineComponent {
    /** The tunnel is up (also after a restart). Apply all configuration to it. */
    suspend fun onTunnelConnected(tunnel: Tunnel)

    /** The tunnel is gone. Drop references to it. */
    suspend fun onTunnelDisconnected() {}
}
