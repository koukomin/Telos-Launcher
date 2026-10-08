package de.mm20.launcher2.network.util

import java.net.InetAddress

/**
 * Helpers for IP literals. Android's `InetAddress.getByName` resolves host names over the network
 * when given anything else than a literal, so every string is checked first.
 */
internal object IpUtil {
    private val v4 = Regex("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$")
    private val v6 = Regex("^[0-9a-fA-F:.]+(%[0-9a-zA-Z._-]+)?$")

    /** Parses an IPv4 or IPv6 literal (brackets allowed) or returns null. Never does a DNS lookup. */
    fun parse(text: String?): InetAddress? {
        val s = text?.trim()?.removePrefix("[")?.removeSuffix("]") ?: return null
        if (s.isEmpty()) return null
        val ok = v4.matches(s) || (s.contains(':') && v6.matches(s))
        if (!ok) return null
        return try {
            InetAddress.getByName(s)
        } catch (e: Exception) {
            null
        }
    }

    fun isUnspecified(text: String?): Boolean = parse(text)?.isAnyLocalAddress == true

    /**
     * Splits `1.2.3.4:443`, `[::1]:443` or a bare address. A missing or invalid port gives 0.
     */
    fun splitHostPort(text: String?): Pair<String, Int> {
        val s = text?.trim().orEmpty()
        if (s.isEmpty()) return "" to 0
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end > 0) {
                val host = s.substring(1, end)
                val port = s.substring(end + 1).removePrefix(":").toIntOrNull() ?: 0
                return host to port
            }
        }
        val colons = s.count { it == ':' }
        if (colons == 1) {
            val i = s.indexOf(':')
            return s.substring(0, i) to (s.substring(i + 1).toIntOrNull() ?: 0)
        }
        // bare IPv6 or something else without a port
        return s to 0
    }

    /** `ip` or `ip:port` normalised to `host:port` with brackets for IPv6, using [defaultPort] when none is given. */
    fun withPort(text: String, defaultPort: Int): String {
        val (host, port) = splitHostPort(text)
        val p = if (port == 0) defaultPort else port
        return if (host.contains(':')) "[$host]:$p" else "$host:$p"
    }

    /** A CIDR range such as `10.0.0.0/8`; a bare address is a /32 or /128. */
    class Cidr(val network: ByteArray, val prefix: Int) {
        fun contains(address: InetAddress): Boolean {
            val b = address.address
            if (b.size != network.size) return false
            var bits = prefix
            var i = 0
            while (bits > 0 && i < b.size) {
                val mask = if (bits >= 8) 0xff else (0xff shl (8 - bits)) and 0xff
                if ((b[i].toInt() and mask) != (network[i].toInt() and mask)) return false
                bits -= 8
                i++
            }
            return true
        }
    }

    fun parseCidr(text: String): Cidr? {
        val parts = text.trim().split('/')
        if (parts.size > 2) return null
        val address = parse(parts[0]) ?: return null
        val max = address.address.size * 8
        val prefix = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else max
        if (prefix < 0 || prefix > max) return null
        return Cidr(address.address, prefix)
    }

    /** Lower case, trimmed, without a trailing dot. */
    fun normalizeDomain(domain: String): String = domain.trim().trimEnd('.').lowercase()
}
