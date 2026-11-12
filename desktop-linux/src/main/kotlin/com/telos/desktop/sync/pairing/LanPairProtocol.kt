package com.telos.desktop.sync.pairing

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Locale

/**
 * VOS-4 LAN pairing URI + bind helpers shared conceptually with the Android client.
 *
 * Payload: `telos-sync://pair?code=&host=&port=` (optional `tls=0|1`).
 * Never log [PairPayload.code] or derived tokens.
 */
object LanPairProtocol {
    const val SCHEME = "telos-sync"
    const val HOST_PART = "pair"
    const val DEFAULT_PORT = 17890
    const val CODE_LENGTH = 8
    const val CODE_TTL_MS = 5 * 60_000L
    /** Crockford-ish alphabet without ambiguous O/0/I/1. */
    private const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val random = SecureRandom()

    data class PairPayload(
        val code: String,
        val host: String,
        val port: Int,
        val tls: Boolean = false,
    ) {
        fun toUri(): String = buildUri(code, host, port, tls)

        fun httpBaseUrl(): String {
            val scheme = if (tls) "https" else "http"
            val hostPart = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
            return "$scheme://$hostPart:$port"
        }
    }

    fun generateCode(length: Int = CODE_LENGTH): String {
        require(length in 4..16) { "code length out of range" }
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)])
        }
        return sb.toString()
    }

    fun normalizeCode(raw: String): String =
        raw.trim().uppercase(Locale.US).filter { it in CODE_ALPHABET }

    fun buildUri(code: String, host: String, port: Int, tls: Boolean = false): String {
        val normalized = normalizeCode(code)
        require(normalized.length >= 4) { "pairing code too short" }
        require(port in 1..65535) { "invalid port" }
        require(host.isNotBlank()) { "host required" }
        val enc = StandardCharsets.UTF_8.name()
        val q = buildString {
            append("code=").append(URLEncoder.encode(normalized, enc))
            append("&host=").append(URLEncoder.encode(host.trim(), enc))
            append("&port=").append(port)
            if (tls) append("&tls=1")
        }
        return "$SCHEME://$HOST_PART?$q"
    }

    fun parseUri(raw: String): PairPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val uri = URI(trimmed)
            if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return@runCatching null
            val query = uri.rawQuery
                ?: uri.schemeSpecificPart.substringAfter('?', missingDelimiterValue = "")
            if (query.isBlank()) return@runCatching null
            val params = parseQuery(query)
            val code = normalizeCode(params["code"].orEmpty())
            val host = params["host"]?.trim().orEmpty()
            val port = params["port"]?.toIntOrNull() ?: DEFAULT_PORT
            val tls = params["tls"] == "1" || params["tls"].equals("true", ignoreCase = true)
            if (code.length < 4 || host.isBlank() || port !in 1..65535) return@runCatching null
            PairPayload(code = code, host = host, port = port, tls = tls)
        }.getOrNull()
    }

    fun parseQuery(query: String): Map<String, String> {
        val enc = StandardCharsets.UTF_8.name()
        return query.split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val eq = part.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val key = URLDecoder.decode(part.substring(0, eq), enc)
                val value = URLDecoder.decode(part.substring(eq + 1), enc)
                key to value
            }
            .toMap()
    }

    /**
     * Returns true only for loopback / RFC1918 / link-local / ULA / CGNAT addresses.
     * Public unicast addresses are rejected for LAN pairing bind.
     */
    fun isPrivateOrLocalAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress) return false
        if (address.isLoopbackAddress) return true
        if (address.isLinkLocalAddress) return true
        if (address.isSiteLocalAddress) return true
        when (address) {
            is Inet4Address -> {
                val b = address.address
                val a0 = b[0].toInt() and 0xff
                val a1 = b[1].toInt() and 0xff
                // Carrier-grade NAT 100.64.0.0/10 — treat as private for LAN pairing.
                if (a0 == 100 && a1 in 64..127) return true
                return false
            }
            is Inet6Address -> {
                val b = address.address
                // Unique local fc00::/7
                if ((b[0].toInt() and 0xfe) == 0xfc) return true
                return false
            }
            else -> return false
        }
    }

    fun requirePrivateBindAddress(host: String): InetAddress {
        val addr = InetAddress.getByName(host)
        require(isPrivateOrLocalAddress(addr)) {
            "LAN pairing must bind a private/local address (rejected: ${addr.hostAddress})"
        }
        return addr
    }

    /** Prefer a non-loopback private IPv4; fall back to loopback. */
    fun suggestLanBindHost(): String {
        val ifaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }
            .getOrDefault(emptyList())
        for (iface in ifaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses) {
                if (addr is Inet4Address && isPrivateOrLocalAddress(addr) && !addr.isLoopbackAddress) {
                    return addr.hostAddress
                }
            }
        }
        for (iface in ifaces) {
            for (addr in iface.inetAddresses) {
                if (addr is Inet4Address && isPrivateOrLocalAddress(addr)) {
                    return addr.hostAddress
                }
            }
        }
        return "127.0.0.1"
    }
}
