package de.mm20.launcher2.network.api

import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.StateFlow

/** The protocols of upstream DNS servers. */
enum class DnsKind {
    /** The DNS servers of the current network (no encryption). Not removable, not editable. */
    System,

    /** Plain DNS over UDP/TCP to an IP address, e.g. `9.9.9.9`. */
    Plain,

    /** DNS over HTTPS. [DnsServer.url] is the `https://` URL. */
    Doh,

    /** DNS over TLS. [DnsServer.url] is `host` or `tls://host[:port]`. */
    Dot,

    /** DNSCrypt. [DnsServer.url] is the `sdns://` stamp, [DnsServer.relay] an optional relay stamp. */
    DnsCrypt,

    /** Oblivious DoH. [DnsServer.url] is the resolver URL, [DnsServer.relay] the proxy URL. */
    Odoh,

    /** A DNS server on this device or LAN reached as `ip:port` (e.g. a local resolver app). [DnsServer.url] is `ip[:port]`, several separated by commas. */
    DnsProxy,
}

/** One upstream DNS server, built in or added by the user. */
@Serializable
data class DnsServer(
    /** Stable id. Built-in servers have fixed ids; custom ones get one from [DnsController.addCustom]. */
    val id: String,
    val kind: DnsKind,
    /** Display name. */
    val name: String,
    /** Kind specific address, see [DnsKind]. Empty for [DnsKind.System]. */
    val url: String = "",
    /** Kind specific second address (relay/proxy), `null` when not used. */
    val relay: String? = null,
    /** Known IP addresses of the server's host, so that it can be reached without a DNS lookup. May be empty. */
    val bootstrapIps: List<String> = emptyList(),
    /** Short description for the list (provider, filtering). Empty for custom servers. */
    val description: String = "",
    /** True for the entries shipped with the app. They cannot be changed or removed. */
    val builtIn: Boolean = false,
)

/** Result of the last attempt of the engine to use a server. */
enum class DnsHealth {
    /** Not in use or not checked yet. */
    Unknown,
    Working,
    Failing,
}

/**
 * Manages the upstream DNS servers and which one Telos Network asks.
 *
 * The engine always asks one *selected* server. The selected server is registered in the Go engine
 * under the transport id `Backend.Preferred` ("Preferred"); [transportFor] tells the DNS callback which
 * id to put into the query.
 */
interface DnsController : EngineComponent {
    /** Built-in and custom servers. Built-ins come first. */
    val servers: StateFlow<List<DnsServer>>

    /** The server that is used. Never null: when nothing was chosen it is the System entry. */
    val selected: StateFlow<DnsServer>

    /** Health per server id as far as the engine knows it. */
    val health: StateFlow<Map<String, DnsHealth>>

    /** Selects a server and applies it to a running tunnel. Returns an error if the id is unknown or the engine rejected the server. */
    suspend fun select(id: String): Result<Unit>

    /**
     * Adds a custom server. The [DnsServer.id] of [server] is ignored and replaced by a new one;
     * the stored entry is returned. Fails when the address does not parse for its [DnsServer.kind].
     */
    suspend fun addCustom(server: DnsServer): Result<DnsServer>

    /** Replaces the custom server with the same id. Built-in servers cannot be updated. */
    suspend fun updateCustom(server: DnsServer): Result<Unit>

    /** Removes a custom server. If it was selected, System becomes selected. Built-in servers cannot be removed. */
    suspend fun remove(id: String): Result<Unit>

    /** Checks whether the address is syntactically valid for the kind. Does no network access. */
    fun validate(server: DnsServer): Result<Unit>

    /**
     * Called by the engine for every DNS question. Returns the id of the Go transport that
     * should answer it: `"Preferred"` normally, `"System"` for the System server or as fallback when the
     * selected server could not be registered. Implementations can return per-app or per-domain transports.
     * Must be fast and must not block.
     */
    fun transportFor(uid: Int, domain: String): String
}
