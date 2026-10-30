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
            allow.writeText("# one host or CIDR per line; empty = loopback + DNS only\n")
        }
        val script = File.createTempFile("telos-nft", ".nft")
        script.writeText(nftRules(allow.readLines()))
        val r = Sys.run("pkexec", "nft", "-f", script.absolutePath, timeoutSec = 20)
        script.delete()
        if (!r.ok) {
            Sys.run("nft", "-f", "/dev/stdin", timeoutSec = 10, input = nftRules(allow.readLines()))
        }
    }

    fun clear() {
        Sys.run("pkexec", "nft", "delete", "table", "inet", "telos_session", timeoutSec = 10)
    }

    private fun nftRules(lines: List<String>): String {
        val extra = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val allow = extra.joinToString("\n") { "        ip daddr $it accept" }
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
}
// === TELOS_PENDING_REVIEW_END: telos_labwc_session_p0p1 ===
