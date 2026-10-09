package de.mm20.launcher2.network.impl.wg

import de.mm20.launcher2.network.api.WgConfigError
import de.mm20.launcher2.network.api.WgConfigException
import de.mm20.launcher2.network.api.WgInterface
import de.mm20.launcher2.network.api.WgPeer
import de.mm20.launcher2.network.api.WireguardConfig
import de.mm20.launcher2.network.util.IpUtil

/** Checks and cleans up configs, whether they come from a `.conf` file, a QR code or the editor. */
internal object WgValidator {
    private val keyRegex = Regex("^[A-Za-z0-9+/]{43}=$")
    private val hostRegex = Regex("^[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$")
    private val domainRegex = Regex("^[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$")

    const val MAX_NAME = 64

    fun isValidKey(key: String?): Boolean = key != null && keyRegex.matches(key.trim())

    /** `host:port` or `[v6]:port` or `v4:port`; the host may be a name. */
    fun isValidEndpoint(endpoint: String): Boolean {
        val e = endpoint.trim()
        val host: String
        val port: String
        if (e.startsWith("[")) {
            val end = e.indexOf(']')
            if (end < 0 || !e.substring(end + 1).startsWith(":")) return false
            host = e.substring(1, end)
            port = e.substring(end + 2)
            if (IpUtil.parse(host) == null || !host.contains(':')) return false
        } else {
            val i = e.lastIndexOf(':')
            if (i <= 0) return false
            host = e.substring(0, i)
            port = e.substring(i + 1)
            if (host.contains(':')) return false
            if (IpUtil.parse(host) == null && !hostRegex.matches(host)) return false
        }
        val p = port.toIntOrNull() ?: return false
        return p in 1..65535
    }

    /**
     * Returns a cleaned copy of [config] (trimmed values, blank optionals turned into null/empty) or
     * throws [WgConfigException] describing the first problem.
     */
    fun normalize(config: WireguardConfig): WireguardConfig {
        val name = config.name.trim()
        if (name.isEmpty() || name.length > MAX_NAME || name.any { it == '\n' || it == '\r' }) {
            throw WgConfigException(WgConfigError.InvalidName)
        }
        val i = config.wgInterface
        val priv = i.privateKey.trim()
        if (priv.isEmpty()) throw WgConfigException(WgConfigError.MissingPrivateKey)
        if (!isValidKey(priv)) throw WgConfigException(WgConfigError.InvalidKey, "PrivateKey")
        val addresses = i.addresses.map { it.trim() }.filter { it.isNotEmpty() }
        if (addresses.isEmpty()) throw WgConfigException(WgConfigError.MissingAddress)
        addresses.forEach { if (IpUtil.parseCidr(it) == null) throw WgConfigException(WgConfigError.InvalidAddress, it) }
        val dns = i.dns.map { it.trim() }.filter { it.isNotEmpty() }
        dns.forEach {
            if (IpUtil.parse(it) == null && !domainRegex.matches(it)) throw WgConfigException(WgConfigError.InvalidDns, it)
        }
        if (i.mtu != 0 && i.mtu !in 576..9200) throw WgConfigException(WgConfigError.InvalidNumber, "MTU")
        if (i.listenPort !in 0..65535) throw WgConfigException(WgConfigError.InvalidNumber, "ListenPort")
        if (config.peers.isEmpty()) throw WgConfigException(WgConfigError.MissingPeer)
        val peers = config.peers.map { p ->
            val pub = p.publicKey.trim()
            if (pub.isEmpty()) throw WgConfigException(WgConfigError.MissingPublicKey)
            if (!isValidKey(pub)) throw WgConfigException(WgConfigError.InvalidKey, "PublicKey")
            val psk = p.presharedKey?.trim()?.takeIf { it.isNotEmpty() }
            if (psk != null && !isValidKey(psk)) throw WgConfigException(WgConfigError.InvalidKey, "PresharedKey")
            val allowed = p.allowedIps.map { it.trim() }.filter { it.isNotEmpty() }
            if (allowed.isEmpty()) throw WgConfigException(WgConfigError.MissingAllowedIps)
            allowed.forEach { if (IpUtil.parseCidr(it) == null) throw WgConfigException(WgConfigError.InvalidAllowedIps, it) }
            val endpoint = p.endpoint?.trim()?.takeIf { it.isNotEmpty() }
            if (endpoint != null && !isValidEndpoint(endpoint)) throw WgConfigException(WgConfigError.InvalidEndpoint, endpoint)
            if (p.persistentKeepalive !in 0..65535) throw WgConfigException(WgConfigError.InvalidNumber, "PersistentKeepalive")
            WgPeer(pub, psk, allowed, endpoint, p.persistentKeepalive)
        }
        return config.copy(
            name = name,
            wgInterface = WgInterface(priv, addresses, dns, i.mtu, i.listenPort),
            peers = peers,
            onlySsids = config.onlySsids.map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
}
