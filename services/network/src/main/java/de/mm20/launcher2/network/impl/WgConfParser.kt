package de.mm20.launcher2.network.impl

import android.util.Base64
import de.mm20.launcher2.network.api.WgInterface
import de.mm20.launcher2.network.api.WgPeer
import de.mm20.launcher2.network.api.WireguardConfig

/** Reads and writes the wg-quick `.conf` format, and converts configs to the userspace format of firestack. */
internal object WgConfParser {

    class ParseException(message: String) : Exception(message)

    /** A config without id; the caller assigns one. [name] falls back to the first comment line. */
    fun parse(text: String, name: String?): WireguardConfig {
        var section = ""
        var comment: String? = null
        var iface = mutableMapOf<String, String>()
        var hasInterface = false
        val peers = mutableListOf<MutableMap<String, String>>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#") || line.startsWith(";")) {
                if (comment == null) comment = line.trimStart('#', ';').trim().takeIf { it.isNotEmpty() }
                continue
            }
            val noComment = line.substringBefore('#').trim()
            if (noComment.startsWith("[") && noComment.endsWith("]")) {
                section = noComment.substring(1, noComment.length - 1).trim().lowercase()
                when (section) {
                    "interface" -> {
                        if (hasInterface) throw ParseException("more than one [Interface]")
                        hasInterface = true
                        iface = mutableMapOf()
                    }
                    "peer" -> peers.add(mutableMapOf())
                    else -> throw ParseException("unknown section [$section]")
                }
                continue
            }
            val eq = noComment.indexOf('=')
            if (eq <= 0) throw ParseException("invalid line: $noComment")
            val key = noComment.substring(0, eq).trim().lowercase()
            val value = noComment.substring(eq + 1).trim()
            when (section) {
                "interface" -> iface[key] = value
                "peer" -> peers.last()[key] = value
                else -> throw ParseException("key outside of a section: $key")
            }
        }
        if (!hasInterface) throw ParseException("missing [Interface]")
        val privateKey = iface["privatekey"] ?: throw ParseException("missing PrivateKey")
        requireKey(privateKey, "PrivateKey")
        val addresses = splitList(iface["address"])
        if (addresses.isEmpty()) throw ParseException("missing Address")
        if (peers.isEmpty()) throw ParseException("missing [Peer]")
        val wgPeers = peers.map { p ->
            val pub = p["publickey"] ?: throw ParseException("missing PublicKey")
            requireKey(pub, "PublicKey")
            p["presharedkey"]?.let { requireKey(it, "PresharedKey") }
            WgPeer(
                publicKey = pub,
                presharedKey = p["presharedkey"],
                allowedIps = splitList(p["allowedips"]).ifEmpty { throw ParseException("missing AllowedIPs") },
                endpoint = p["endpoint"],
                persistentKeepalive = p["persistentkeepalive"]?.toIntOrNull() ?: 0,
            )
        }
        return WireguardConfig(
            id = 0,
            name = name?.takeIf { it.isNotBlank() } ?: comment ?: "WireGuard",
            wgInterface = WgInterface(
                privateKey = privateKey,
                addresses = addresses,
                dns = splitList(iface["dns"]),
                mtu = iface["mtu"]?.toIntOrNull() ?: 0,
                listenPort = iface["listenport"]?.toIntOrNull() ?: 0,
            ),
            peers = wgPeers,
        )
    }

    fun export(config: WireguardConfig): String = buildString {
        appendLine("# ${config.name}")
        appendLine("[Interface]")
        appendLine("PrivateKey = ${config.wgInterface.privateKey}")
        appendLine("Address = ${config.wgInterface.addresses.joinToString(", ")}")
        if (config.wgInterface.dns.isNotEmpty()) appendLine("DNS = ${config.wgInterface.dns.joinToString(", ")}")
        if (config.wgInterface.mtu > 0) appendLine("MTU = ${config.wgInterface.mtu}")
        if (config.wgInterface.listenPort > 0) appendLine("ListenPort = ${config.wgInterface.listenPort}")
        config.peers.forEach { p ->
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${p.publicKey}")
            p.presharedKey?.let { appendLine("PresharedKey = $it") }
            appendLine("AllowedIPs = ${p.allowedIps.joinToString(", ")}")
            p.endpoint?.let { appendLine("Endpoint = $it") }
            if (p.persistentKeepalive > 0) appendLine("PersistentKeepalive = ${p.persistentKeepalive}")
        }
    }

    /** The "key=value" text firestack's `Proxies.addProxy` takes for a WireGuard proxy (hex keys). */
    fun toUserspace(config: WireguardConfig): String = buildString {
        val i = config.wgInterface
        append("private_key=").append(hex(i.privateKey)).append('\n')
        if (i.listenPort > 0) append("listen_port=").append(i.listenPort).append('\n')
        // non-standard extension: address, dns and mtu are required by firestack
        append("address=").append(i.addresses.joinToString(",")).append('\n')
        append("dns=").append(i.dns.joinToString(",")).append('\n')
        append("mtu=").append(if (i.mtu > 0) i.mtu else DEFAULT_MTU).append('\n')
        append("replace_peers=true\n")
        config.peers.forEach { p ->
            append("public_key=").append(hex(p.publicKey)).append('\n')
            p.allowedIps.forEach { append("allowed_ip=").append(it).append('\n') }
            p.endpoint?.let { append("endpoint=").append(it).append('\n') }
            if (p.persistentKeepalive > 0) append("persistent_keepalive_interval=").append(p.persistentKeepalive).append('\n')
            p.presharedKey?.let { append("preshared_key=").append(hex(it)).append('\n') }
        }
    }

    fun hex(base64Key: String): String =
        Base64.decode(base64Key, Base64.DEFAULT).joinToString("") { "%02x".format(it) }

    private fun requireKey(key: String, what: String) {
        val bytes = try {
            Base64.decode(key, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            throw ParseException("$what is not base64")
        }
        if (bytes.size != 32) throw ParseException("$what must be 32 bytes")
    }

    private fun splitList(value: String?): List<String> =
        value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private const val DEFAULT_MTU = 1280
}
