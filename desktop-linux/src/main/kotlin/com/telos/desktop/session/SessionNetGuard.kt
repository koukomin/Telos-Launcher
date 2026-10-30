package com.telos.desktop.session

import com.telos.desktop.linux.Sys
import com.telos.desktop.vault.PortableEnvironmentIsolator
import java.io.File

// === TELOS_PENDING_REVIEW_START: telos_labwc_session_p0p1 ===
object SessionNetGuard {
    fun allowlistFile(): File {
        val home = PortableEnvironmentIsolator.vaultHome
            ?: File(System.getProperty("user.home"))
        return File(home, ".config/telos/net-allow.txt").apply { parentFile.mkdirs() }
    }

    fun apply() {
        val allow = allowlistFile()
        if (!allow.exists()) {
            allow.writeText("# one IPv4/IPv6 CIDR per line; empty = loopback + DNS only\n")
        }
        val script = File.createTempFile("telos-nft", ".nft")
        script.writeText(nftRules(allow.readLines()))
        runCatching {
            script.setReadable(false, false)
            script.setWritable(false, false)
            script.setReadable(true, true)
            script.setWritable(true, true)
        }
        val r = Sys.run("pkexec", "nft", "-f", script.absolutePath, timeoutSec = 20)
        if (!r.ok) {
            Sys.run("nft", "-f", script.absolutePath, timeoutSec = 10)
        }
        script.delete()
    }

    fun clear() {
        Sys.run("pkexec", "nft", "delete", "table", "inet", "telos_session", timeoutSec = 10)
        Sys.run("nft", "delete", "table", "inet", "telos_session", timeoutSec = 8)
    }

    internal fun nftRules(lines: List<String>): String {
        val allow = parseAllowEntries(lines).joinToString("\n")
        return """
            destroy table inet telos_session
            table inet telos_session {
              chain output {
                type filter hook output priority 0; policy drop;
                oif "lo" accept
                udp dport 53 accept
                tcp dport 53 accept
                udp dport 853 accept
                tcp dport 853 accept
                ct state established,related accept
            $allow
              }
            }
        """.trimIndent()
    }

    internal fun parseAllowEntries(lines: List<String>): List<String> =
        lines.map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull(::nftLineFor)

    private fun nftLineFor(entry: String): String? {
        val slash = entry.indexOf('/')
        val addr = if (slash >= 0) entry.substring(0, slash) else entry
        val prefix = if (slash >= 0) entry.substring(slash + 1).toIntOrNull() else null
        if (slash >= 0 && prefix == null) return null
        return when {
            isIpv4(addr) && (prefix == null || prefix in 0..32) ->
                "        ip daddr $entry accept"
            isIpv6(addr) && (prefix == null || prefix in 0..128) ->
                "        ip6 daddr $entry accept"
            else -> null
        }
    }

    private fun isIpv4(addr: String): Boolean {
        val parts = addr.split('.')
        if (parts.size != 4) return false
        return parts.all { p ->
            val n = p.toIntOrNull() ?: return false
            n in 0..255 && p == n.toString()
        }
    }

    private fun isIpv6(addr: String): Boolean {
        if (!addr.contains(':') || addr.any { it !in "0123456789abcdefABCDEF:" }) return false
        val groups = addr.split(':')
        if (groups.size > 8) return false
        return groups.all { it.isEmpty() || (it.length <= 4 && it.all { c -> c in "0123456789abcdefABCDEF" }) }
    }
}
// === TELOS_PENDING_REVIEW_END: telos_labwc_session_p0p1 ===
