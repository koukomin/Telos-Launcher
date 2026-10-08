package de.mm20.launcher2.network.api

import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.StateFlow

/** The [Interface] section of a WireGuard config. */
@Serializable
data class WgInterface(
    /** Base64 private key. Never log this. */
    val privateKey: String,
    /** Addresses of the interface in CIDR form, e.g. `10.2.0.2/32`. */
    val addresses: List<String>,
    /** DNS servers (IPs) or search domains the config asks for. */
    val dns: List<String> = emptyList(),
    val mtu: Int = 0,
    /** 0 = random. */
    val listenPort: Int = 0,
)

/** A [Peer] section. */
@Serializable
data class WgPeer(
    /** Base64 public key. */
    val publicKey: String,
    val presharedKey: String? = null,
    /** Traffic for these CIDRs goes to this peer, e.g. `0.0.0.0/0, ::/0`. */
    val allowedIps: List<String>,
    /** `host:port` or `[ipv6]:port`. */
    val endpoint: String? = null,
    /** Seconds, 0 = off. */
    val persistentKeepalive: Int = 0,
)

/** A WireGuard tunnel config. */
@Serializable
data class WireguardConfig(
    /** Unique id, >= 1. The Go engine knows the tunnel as `"wg" + id`. */
    val id: Int,
    val name: String,
    val wgInterface: WgInterface,
    val peers: List<WgPeer>,
    /** Whether the engine should bring this tunnel up. */
    val enabled: Boolean = false,
    /** Use this tunnel only on mobile data. */
    val mobileOnly: Boolean = false,
    /** If non-empty, use this tunnel only on wifi networks with one of these SSIDs. */
    val onlySsids: List<String> = emptyList(),
    /** When the tunnel is down, block the apps that use it instead of letting them go direct. */
    val lockdown: Boolean = false,
) {
    /** The id of the proxy in the Go engine. */
    val proxyId: String get() = "wg$id"
}

/** Runtime state of a tunnel. */
enum class WgStatus {
    /** Not enabled or no tunnel running. */
    Off,
    Connecting,
    Up,
    /** Enabled but the peer does not answer. */
    Down,
    Error,
}

/** Which WireGuard config an app (or everything) uses. */
@Serializable
sealed interface WgAssignment {
    /** The app follows the system default config; if there is none the app goes direct. */
    @Serializable
    data object SystemDefault : WgAssignment

    /** The app always goes direct, even if a system default config is set. */
    @Serializable
    data object Direct : WgAssignment

    /** The app uses this config. */
    @Serializable
    data class Config(val configId: Int) : WgAssignment
}

/** Where a connection should go. */
sealed interface WgRoute {
    /** Normal route, no WireGuard. */
    data object Direct : WgRoute

    /** Drop the connection (lockdown while the assigned tunnel is down). */
    data object Block : WgRoute

    /** Through this proxy id of the engine (`"wg" + id`). */
    data class Via(val proxyId: String) : WgRoute
}

/**
 * Manages WireGuard configs and which apps use which. WireGuard runs inside the Go engine, no kernel
 * module and no separate VPN is involved; Telos Network *is* the VPN and routes selected apps into a tunnel.
 */
interface WireguardController : EngineComponent {
    /** All configs, sorted by name. */
    val configs: StateFlow<List<WireguardConfig>>

    /** Status per config id. */
    val status: StateFlow<Map<Int, WgStatus>>

    /** The config used by apps with [WgAssignment.SystemDefault], or `null` for none. */
    val systemDefault: StateFlow<Int?>

    /** Explicit assignments per app id. Apps not in the map are [WgAssignment.SystemDefault]. */
    val assignments: StateFlow<Map<Int, WgAssignment>>

    /**
     * Parses the text of a `.conf` file (wg-quick format) and stores it as a new config, disabled.
     * @param name display name; when null the name is taken from the first comment or "WireGuard n"
     */
    suspend fun importConf(text: String, name: String? = null): Result<WireguardConfig>

    /** Serializes a config back to `.conf` text (for export/share). */
    fun exportConf(id: Int): String?

    /** Stores a new config; its id is replaced by a fresh one. */
    suspend fun add(config: WireguardConfig): Result<WireguardConfig>

    /** Replaces the config with the same id and re-applies it if it is enabled. */
    suspend fun update(config: WireguardConfig): Result<Unit>

    /** Removes a config; apps assigned to it fall back to [WgAssignment.SystemDefault]. */
    suspend fun remove(id: Int)

    /** Brings a tunnel up or down (needs a running VPN to have an effect; the flag is remembered). */
    suspend fun setEnabled(id: Int, enabled: Boolean)

    /** Sets the config for apps without own assignment. `null` = none. */
    suspend fun setSystemDefault(configId: Int?)

    /** Assigns an app. [WgAssignment.SystemDefault] removes the entry. */
    suspend fun assign(appId: Int, assignment: WgAssignment)

    /** A new random private key, base64. */
    suspend fun generatePrivateKey(): Result<String>

    /**
     * Called by the engine after the firewall allowed a connection, to find out where it goes.
     * Fast, no blocking, never throws.
     */
    fun routeFor(flow: FlowInfo): WgRoute
}
