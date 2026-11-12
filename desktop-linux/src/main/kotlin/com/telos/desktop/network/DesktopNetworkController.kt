package com.telos.desktop.network

import com.telos.desktop.linux.Sys
import com.telos.desktop.panel.ConnectivityManager
import com.telos.desktop.panel.ConnectivityState
import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.platform.TelosRuntimePlatform
import com.telos.desktop.session.SessionNetGuard
import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.TelosSessionAuditLog
import java.io.File
import java.util.Properties

object DesktopNetworkController {
    const val WIREGUARD_CONF_NAME = "telos-wg.conf"
    const val DEFAULT_INTERFACE = "telos-wg"
    private const val TOR_SOCKS_PORT = 9050

    private val isLinux: Boolean
        get() = PlatformEnvironment.current == TelosRuntimePlatform.LinuxDesktop

    fun configHome(): File {
        val home = PortableEnvironmentIsolator.vaultHome ?: File(System.getProperty("user.home"))
        return File(home, ".config/telos").apply { mkdirs() }
    }

    fun wireguardDir(): File = File(configHome(), "wireguard").apply { mkdirs() }

    fun wireguardConfFile(): File = File(wireguardDir(), WIREGUARD_CONF_NAME)

    fun dnsPreferenceFile(): File = File(configHome(), "dns.txt")

    fun torDataDir(): File = File(configHome(), "tor/data").apply { mkdirs() }

    fun torConfigFile(): File = File(configHome(), "tor/torrc").apply { parentFile?.mkdirs() }

    fun torPidFile(): File = File(configHome(), "tor/tor.pid")

    data class WireGuardStatus(
        val backend: String?,
        val running: Boolean,
        val interfaceName: String?,
        val detail: String,
    )

    data class TorStatus(
        val binaryPresent: Boolean,
        val socksListening: Boolean,
        val running: Boolean,
    )

    enum class DnsApplyResult {
        Applied,
        PreferenceOnly,
        Failed,
        Invalid,
    }

    enum class DnsKind { Plain, DoT, DoH }

    data class DnsPreference(val kind: DnsKind, val address: String, val raw: String)

    fun snapshotConnectivity(): ConnectivityState =
        runCatching { ConnectivityManager.snapshot() }.getOrElse {
            ConnectivityState(false, null, null, emptyList(), false, emptyList())
        }

    fun firewallAllowlistPath(): File = SessionNetGuard.allowlistFile()

    fun readAllowlistText(): String {
        val file = firewallAllowlistPath()
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText("# one IPv4/IPv6 CIDR per line; empty = loopback + DNS only\n")
        }
        return file.readText()
    }

    fun writeAllowlistText(text: String) {
        firewallAllowlistPath().apply { parentFile?.mkdirs() }.writeText(text)
        TelosSessionAuditLog.append("network_policy", "allowlist_updated")
    }

    fun applySessionFirewall(): Boolean {
        val ok = SessionNetGuard.apply()
        TelosSessionAuditLog.append("network_policy", if (ok) "firewall_applied" else "firewall_apply_failed")
        return ok
    }

    fun clearSessionFirewall() {
        SessionNetGuard.clear()
        TelosSessionAuditLog.append("network_policy", "firewall_cleared")
    }

    fun sessionFirewallActive(): Boolean {
        if (!isLinux) return false
        val r = Sys.run("nft", "list", "table", "inet", "telos_session", timeoutSec = 6)
        return r.ok && r.stdout.contains("telos_session")
    }

    enum class WireGuardBackend { WgQuick, Wg }

    fun detectWireGuardBackend(): WireGuardBackend? = when {
        Sys.which("wg-quick") != null -> WireGuardBackend.WgQuick
        Sys.which("wg") != null -> WireGuardBackend.Wg
        else -> null
    }

    fun importWireGuardConfig(text: String) {
        wireguardConfFile().writeText(text)
    }

    fun startWireGuard(): Boolean {
        if (!isLinux) return false
        val conf = wireguardConfFile()
        if (!conf.isFile) return false
        val backend = detectWireGuardBackend() ?: return false
        return when (backend) {
            WireGuardBackend.WgQuick -> {
                val quick = Sys.which("wg-quick") ?: return false
                val ok = Sys.run(quick, "up", conf.absolutePath, timeoutSec = 30).ok
                TelosSessionAuditLog.append("network_policy", if (ok) "wireguard_start" else "wireguard_start_failed")
                ok
            }
            WireGuardBackend.Wg -> {
                val iface = interfaceNameFromConf(conf.readText())
                if (Sys.run("ip", "link", "show", iface).exit != 0) {
                    Sys.run("ip", "link", "add", "dev", iface, "type", "wireguard", timeoutSec = 8)
                }
                val okConf = Sys.run("wg", "setconf", iface, conf.absolutePath, timeoutSec = 15).ok
                val up = Sys.run("ip", "link", "set", "up", "dev", iface, timeoutSec = 8).ok
                val ok = okConf && up
                TelosSessionAuditLog.append("network_policy", if (ok) "wireguard_start" else "wireguard_start_failed")
                ok
            }
        }
    }

    fun stopWireGuard(): Boolean {
        if (!isLinux) return false
        val result = stopWireGuardInner()
        TelosSessionAuditLog.append("network_policy", if (result) "wireguard_stop" else "wireguard_stop_failed")
        return result
    }

    private fun stopWireGuardInner(): Boolean {
        if (!isLinux) return false
        val conf = wireguardConfFile()
        val backend = detectWireGuardBackend() ?: return false
        return when (backend) {
            WireGuardBackend.WgQuick -> {
                val quick = Sys.which("wg-quick") ?: return false
                if (conf.isFile) {
                    Sys.run(quick, "down", conf.absolutePath, timeoutSec = 20).ok
                } else {
                    Sys.run(quick, "down", DEFAULT_INTERFACE, timeoutSec = 20).ok
                }
            }
            WireGuardBackend.Wg -> {
                val iface = interfaceNameFromConf(conf.takeIf { it.isFile }?.readText().orEmpty())
                Sys.run("ip", "link", "del", "dev", iface, timeoutSec = 8).ok ||
                    Sys.run("ip", "link", "set", "down", "dev", iface, timeoutSec = 8).ok
            }
        }
    }

    fun wireGuardStatus(): WireGuardStatus {
        if (!isLinux) {
            return WireGuardStatus(null, false, null, "—")
        }
        val backend = detectWireGuardBackend()?.name
        val iface = interfaceNameFromConf(
            wireguardConfFile().takeIf { it.isFile }?.readText().orEmpty(),
        )
        val show = Sys.run("wg", "show", timeoutSec = 6)
        val running = show.ok && show.stdout.isNotBlank() &&
            (iface.isEmpty() || show.stdout.contains(iface))
        val detail = if (running) {
            show.stdout.lines().firstOrNull().orEmpty().ifBlank { iface }
        } else {
            "—"
        }
        return WireGuardStatus(backend, running, iface.takeIf { it.isNotBlank() }, detail)
    }

    fun torAvailable(): Boolean = Sys.which("tor") != null

    fun startTor(): Boolean {
        if (!isLinux || !torAvailable()) return false
        torDataDir().mkdirs()
        val torrc = torConfigFile()
        torrc.writeText(buildTorRc(torDataDir(), torPidFile(), TOR_SOCKS_PORT))
        val tor = Sys.which("tor") ?: return false
        if (torPidFile().isFile) {
            val pid = torPidFile().readText().trim().toIntOrNull()
            if (pid != null && processAlive(pid)) return true
        }
        val ok = Sys.runBg(tor, "-f", torrc.absolutePath)
        TelosSessionAuditLog.append("network_policy", if (ok) "tor_start" else "tor_start_failed")
        return ok
    }

    fun stopTor(): Boolean {
        if (!isLinux) return false
        val pid = torPidFile().takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
        if (pid != null) {
            Sys.run("kill", pid.toString(), timeoutSec = 4)
            torPidFile().delete()
            TelosSessionAuditLog.append("network_policy", "tor_stop")
            return true
        }
        val marker = torConfigFile().absolutePath
        Sys.run("pkill", "-f", marker, timeoutSec = 4)
        torPidFile().delete()
        TelosSessionAuditLog.append("network_policy", "tor_stop")
        return true
    }

    fun torStatus(): TorStatus {
        val bin = torAvailable()
        val socks = torSocksListening()
        val pid = torPidFile().takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
        val proc = pid != null && processAlive(pid)
        return TorStatus(bin, socks, socks || proc)
    }

    fun readDnsPreference(): String =
        dnsPreferenceFile().takeIf { it.isFile }?.readText().orEmpty()

    fun writeDnsPreference(text: String) {
        dnsPreferenceFile().writeText(text.trim() + "\n")
        TelosSessionAuditLog.append("network_policy", "dns_preference_updated")
    }

    fun applyDnsPreference(): DnsApplyResult {
        if (!isLinux) return DnsApplyResult.PreferenceOnly
        val line = readDnsPreference().lines().map { it.trim() }.firstOrNull {
            it.isNotEmpty() && !it.startsWith("#")
        } ?: return DnsApplyResult.PreferenceOnly
        val pref = parseDnsLine(line) ?: return DnsApplyResult.Invalid
        if (Sys.which("resolvectl") == null) return DnsApplyResult.PreferenceOnly
        val link = defaultResolveLink() ?: return DnsApplyResult.Failed
        return when (pref.kind) {
            DnsKind.DoH -> DnsApplyResult.PreferenceOnly
            DnsKind.DoT -> {
                val a = Sys.run("resolvectl", "dns", link, pref.address, timeoutSec = 8)
                val tls = Sys.run("resolvectl", "dnsovertls", link, "yes", timeoutSec = 8)
                if (a.ok && tls.ok) DnsApplyResult.Applied else DnsApplyResult.Failed
            }
            DnsKind.Plain -> {
                Sys.run("resolvectl", "dnsovertls", link, "no", timeoutSec = 8)
                if (Sys.run("resolvectl", "dns", link, pref.address, timeoutSec = 8).ok) {
                    DnsApplyResult.Applied
                } else {
                    DnsApplyResult.Failed
                }
            }
        }
    }

    fun parseAllowEntries(lines: List<String>): List<String> = SessionNetGuard.parseAllowEntries(lines)

    fun redactWireGuardSecrets(config: String): String =
        config.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("PrivateKey", ignoreCase = true) ||
                trimmed.startsWith("PresharedKey", ignoreCase = true)
            ) {
                line.substringBefore('=') + "= <redacted>"
            } else {
                line
            }
        }

    fun interfaceNameFromConf(config: String): String {
        if (config.isBlank()) return DEFAULT_INTERFACE
        var inInterface = false
        for (line in config.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.equals("[Interface]", ignoreCase = true) -> inInterface = true
                trimmed.startsWith("[") -> inInterface = false
                inInterface && trimmed.startsWith("Name", ignoreCase = true) -> {
                    return trimmed.substringAfter('=').trim().ifEmpty { DEFAULT_INTERFACE }
                }
            }
        }
        return DEFAULT_INTERFACE
    }

    fun parseDnsLine(line: String): DnsPreference? {
        val raw = line.trim()
        if (raw.isEmpty()) return null
        return when {
            raw.startsWith("https://", ignoreCase = true) ->
                DnsPreference(DnsKind.DoH, raw, raw)
            raw.startsWith("tls://", ignoreCase = true) || raw.startsWith("dot://", ignoreCase = true) -> {
                val host = raw.substringAfter("://").substringBefore('/').trim()
                if (host.isEmpty()) null else DnsPreference(DnsKind.DoT, host, raw)
            }
            else -> {
                val host = raw.substringBefore('#').trim()
                if (host.isEmpty()) null else DnsPreference(DnsKind.Plain, host, raw)
            }
        }
    }

    fun buildTorRc(dataDir: File, pidFile: File, socksPort: Int): String =
        """
            DataDirectory ${dataDir.absolutePath}
            SocksPort $socksPort
            PidFile ${pidFile.absolutePath}
            Log notice file ${File(dataDir.parentFile, "tor.log").absolutePath}
        """.trimIndent() + "\n"

    private fun torSocksListening(): Boolean {
        if (!isLinux) return false
        val ss = Sys.run("ss", "-ltn", "sport", "=", ":$TOR_SOCKS_PORT", timeoutSec = 4)
        if (ss.ok && ss.stdout.contains(":$TOR_SOCKS_PORT")) return true
        val nc = Sys.which("nc")
        if (nc != null) {
            val probe = Sys.run(nc, "-z", "127.0.0.1", TOR_SOCKS_PORT.toString(), timeoutSec = 3)
            return probe.ok
        }
        return false
    }

    private fun defaultResolveLink(): String? {
        val status = Sys.run("resolvectl", "status", timeoutSec = 6)
        if (!status.ok) return null
        Regex("^Link \\d+ \\(([^)]+)\\)", RegexOption.MULTILINE)
            .findAll(status.stdout)
            .firstOrNull { it.groupValues[1] != "lo" }
            ?.groupValues?.get(1)?.let { return it }
        return Regex("^Link \\d+ \\(([^)]+)\\)", RegexOption.MULTILINE)
            .find(status.stdout)?.groupValues?.get(1)
    }

    private fun processAlive(pid: Int): Boolean {
        if (!isLinux) return false
        val r = Sys.run("kill", "-0", pid.toString(), timeoutSec = 2)
        return r.ok
    }

    fun vaultDesktopProperties(vaultConfigDir: File?): Properties {
        val p = Properties()
        val file = vaultConfigDir?.let { File(it, "desktop.properties") }?.takeIf { it.isFile } ?: return p
        file.inputStream().use { p.load(it) }
        return p
    }

    fun activeProfileName(vaultConfigDir: File? = null): String? {
        val fromVault = vaultDesktopProperties(vaultConfigDir).getProperty("profile.active")?.trim()
        if (!fromVault.isNullOrEmpty()) return fromVault
        val marker = File(configHome(), "profile.active")
        return marker.takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }
    }
}
