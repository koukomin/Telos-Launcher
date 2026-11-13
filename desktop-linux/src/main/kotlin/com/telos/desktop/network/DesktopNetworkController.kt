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
    private val NEXTDNS_ID = Regex("^[0-9a-zA-Z]{4,12}$")
    private val DNS_DOMAIN_LINE = Regex(
        "^(?:\\*\\.)?(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$",
    )

    private val isLinux: Boolean
        get() = PlatformEnvironment.current == TelosRuntimePlatform.LinuxDesktop

    @Volatile
    private var configHomeDir: File? = null

    /**
     * Vault/session config root (`~/.config/telos` or portable equivalent).
     * @param create mkdir on first use (writes). Cold-start path probes use false.
     */
    fun configHome(create: Boolean = true): File {
        val cached = configHomeDir
        if (cached != null) {
            if (create) cached.mkdirs()
            return cached
        }
        val home = PortableEnvironmentIsolator.vaultHome ?: File(System.getProperty("user.home"))
        val dir = File(home, ".config/telos")
        if (create) dir.mkdirs()
        configHomeDir = dir
        return dir
    }

    /** Clears the cached config home (tests / profile switch). */
    fun clearConfigHomeCache() {
        configHomeDir = null
    }

    fun wireguardDir(): File = File(configHome(create = true), "wireguard").apply { mkdirs() }

    fun wireguardConfFile(): File = File(wireguardDir(), WIREGUARD_CONF_NAME)

    fun dnsPreferenceFile(): File = File(configHome(), "dns.txt")

    /** One blocked hostname/domain per line (vault preference; local stub optional). */
    fun dnsBlocklistFile(): File = File(configHome(), "dns-block.txt")

    /** One allowlisted hostname/domain per line (exceptions over the blocklist). */
    fun dnsAllowlistFile(): File = File(configHome(), "dns-allow.txt")

    /** Optional NextDNS configuration ID (4–12 alnum). Used for resolvectl DoT when available. */
    fun nextDnsConfigIdFile(): File = File(configHome(), "nextdns-id.txt")

    /**
     * Optional local DNS stub listen port. When set (or stub enabled), Telos starts a local
     * UDP/TCP forwarder on 127.0.0.1 and can redirect outbound DNS via nftables `inet telos_dns`.
     */
    fun dnsStubPortFile(): File = File(configHome(), "dns-stub.port")

    /** User toggle for the built-in Telos DNS stub (true|false). */
    fun dnsStubEnabledFile(): File = File(configHome(), "dns-stub.enabled")

    fun killSwitchPreferenceFile(): File = File(configHome(), "kill-switch.txt")

    fun appRoutingFile(): File = File(configHome(), "app-routing.txt")

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

    /**
     * Honest kill-switch status for the Network UI.
     * Linux can enforce via nftables `inet telos_session`; Windows/macOS stay preference-only.
     */
    enum class KillSwitchStatus {
        Disabled,
        PreferenceOnly,
        Enforced,
        Failed,
    }

    enum class AppRouteMode {
        Allow,
        Deny,
        Vpn,
    }

    data class AppRoute(val id: String, val mode: AppRouteMode)

    enum class DnsApplyResult {
        Applied,
        PreferenceOnly,
        Failed,
        Invalid,
    }

    /**
     * Honest DNS-filter enforcement status for NextDNS-class foundations.
     * Local host lists alone never claim filtering without a stub + nft redirect.
     */
    enum class DnsFilterStatus {
        Disabled,
        PreferenceOnly,
        Enforced,
        Failed,
        Invalid,
    }

    enum class DnsKind { Plain, DoT, DoH }

    data class DnsPreference(val kind: DnsKind, val address: String, val raw: String)

    data class DnsFilterSnapshot(
        val blocklist: List<String>,
        val allowlist: List<String>,
        val nextDnsConfigId: String?,
        val stubPort: Int?,
        val status: DnsFilterStatus,
    )

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

    /**
     * Clears the session nftables table unless the kill-switch preference is on.
     * Returns true when the table was cleared; false when refused or not Linux.
     */
    fun clearSessionFirewall(): Boolean {
        if (readKillSwitchEnabled()) {
            ensureKillSwitch()
            return false
        }
        SessionNetGuard.clear()
        TelosSessionAuditLog.append("network_policy", "firewall_cleared")
        return true
    }

    fun sessionFirewallActive(): Boolean {
        if (!isLinux) return false
        val r = Sys.run("nft", "list", "table", "inet", "telos_session", timeoutSec = 6)
        return r.ok && r.stdout.contains("telos_session")
    }

    fun readKillSwitchEnabled(): Boolean {
        val file = killSwitchPreferenceFile()
        if (!file.isFile) return false
        val line = file.readLines().map { it.trim() }.firstOrNull {
            it.isNotEmpty() && !it.startsWith("#")
        } ?: return false
        return line.equals("true", ignoreCase = true) ||
            line.equals("on", ignoreCase = true) ||
            line == "1"
    }

    fun writeKillSwitchEnabled(enabled: Boolean) {
        killSwitchPreferenceFile().apply { parentFile?.mkdirs() }.writeText(
            buildString {
                appendLine("# Telos session kill-switch: true|false")
                appendLine("# When true, Linux keeps nftables inet telos_session default-drop")
                appendLine("# even if WireGuard/Tor stop; clear is refused until turned off.")
                appendLine("# Windows/macOS store the preference only (not applied).")
                appendLine(if (enabled) "true" else "false")
            },
        )
    }

    /**
     * If the kill-switch preference is on, (re)apply session nftables on Linux.
     * Portable platforms never claim enforcement.
     */
    fun ensureKillSwitch(): KillSwitchStatus {
        if (!readKillSwitchEnabled()) return KillSwitchStatus.Disabled
        if (!isLinux || !PlatformEnvironment.settings.showNftSessionNetGuard) {
            return KillSwitchStatus.PreferenceOnly
        }
        if (sessionFirewallActive()) return KillSwitchStatus.Enforced
        return if (applySessionFirewall() && sessionFirewallActive()) {
            KillSwitchStatus.Enforced
        } else {
            KillSwitchStatus.Failed
        }
    }

    fun killSwitchStatus(): KillSwitchStatus {
        if (!readKillSwitchEnabled()) return KillSwitchStatus.Disabled
        if (!isLinux || !PlatformEnvironment.settings.showNftSessionNetGuard) {
            return KillSwitchStatus.PreferenceOnly
        }
        return if (sessionFirewallActive()) KillSwitchStatus.Enforced else KillSwitchStatus.Failed
    }

    fun setKillSwitchEnabled(enabled: Boolean): KillSwitchStatus {
        writeKillSwitchEnabled(enabled)
        return if (enabled) {
            ensureKillSwitch()
        } else {
            KillSwitchStatus.Disabled
        }
    }

    fun readAppRoutingText(): String {
        val file = appRoutingFile()
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(defaultAppRoutingTemplate())
        }
        return file.readText()
    }

    fun writeAppRoutingText(text: String) {
        appRoutingFile().apply { parentFile?.mkdirs() }.writeText(text)
    }

    fun readAppRoutes(): List<AppRoute> = parseAppRoutingLines(readAppRoutingText().lines())

    fun writeAppRoutes(routes: List<AppRoute>) {
        writeAppRoutingText(serializeAppRoutes(routes))
    }

    fun parseAppRoutingLines(lines: List<String>): List<AppRoute> =
        lines.map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 2) return@mapNotNull null
                val id = parts[0].trim()
                if (id.isEmpty() || id.contains('/')) return@mapNotNull null
                val mode = when (parts[1].trim().lowercase()) {
                    "allow" -> AppRouteMode.Allow
                    "deny", "block" -> AppRouteMode.Deny
                    "vpn", "wireguard", "wg" -> AppRouteMode.Vpn
                    else -> return@mapNotNull null
                }
                AppRoute(id, mode)
            }

    fun serializeAppRoutes(routes: List<AppRoute>): String =
        buildString {
            append(defaultAppRoutingTemplate().trimEnd())
            appendLine()
            routes.forEach { route ->
                appendLine("${route.id} ${route.mode.name.lowercase()}")
            }
        }

    fun defaultAppRoutingTemplate(): String =
        """
            # Telos per-app routing (desktop-id or package → allow|deny|vpn)
            # Persisted under the vault ~/.config/telos/app-routing.txt
            # Linux: DesktopFileLauncher consults this table (deny blocks launch;
            # vpn sets TELOS_APP_ROUTE=vpn best-effort — no fake netns).
            # Process-level nftables owner/cgroup routing is not applied (avoids
            # breaking the session kill-switch). Kill-switch still drops non-allowlisted egress.
            # Windows/macOS: preference only.
        """.trimIndent() + "\n"

    /**
     * True when Linux has deny rules and the launcher consults the table (launch-gate).
     * Does not claim nftables owner/cgroup enforcement.
     */
    fun appRoutingEnforced(routes: List<AppRoute> = readAppRoutes()): Boolean =
        isLinux && routes.any { it.mode == AppRouteMode.Deny }

    fun resolveAppRoute(
        candidates: Collection<String>,
        routes: List<AppRoute> = readAppRoutes(),
    ): AppRoute? {
        if (candidates.isEmpty() || routes.isEmpty()) return null
        val normalized = candidates.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return routes.firstOrNull { route ->
            val id = route.id.trim()
            id in normalized ||
                id.removeSuffix(".desktop") in normalized ||
                "$id.desktop" in normalized
        }
    }

    enum class LaunchRouteDecision {
        Allow,
        Deny,
        Vpn,
    }

    fun launchRouteDecision(
        candidates: Collection<String>,
        routes: List<AppRoute> = readAppRoutes(),
    ): LaunchRouteDecision {
        val match = resolveAppRoute(candidates, routes) ?: return LaunchRouteDecision.Allow
        return when (match.mode) {
            AppRouteMode.Allow -> LaunchRouteDecision.Allow
            AppRouteMode.Deny -> LaunchRouteDecision.Deny
            AppRouteMode.Vpn -> LaunchRouteDecision.Vpn
        }
    }

    fun desktopIdCandidates(fileName: String, exec: String): List<String> {
        val out = LinkedHashSet<String>()
        val base = fileName.substringAfterLast('/').trim()
        if (base.isNotEmpty()) {
            out += base
            if (base.endsWith(".desktop", ignoreCase = true)) {
                out += base.removeSuffix(".desktop").removeSuffix(".Desktop")
            } else {
                out += "$base.desktop"
            }
        }
        val tokens = exec.trim().split(Regex("\\s+"))
        val flatpakIdx = tokens.indexOfFirst { it == "flatpak" || it.endsWith("/flatpak") }
        if (flatpakIdx >= 0) {
            var i = flatpakIdx + 1
            while (i < tokens.size && tokens[i].startsWith("-")) i++
            if (i < tokens.size && tokens[i] == "run") {
                i++
                while (i < tokens.size && tokens[i].startsWith("-")) i++
                if (i < tokens.size) {
                    val appId = tokens[i].trim()
                    if (appId.isNotEmpty() && !appId.startsWith("-")) out += appId
                }
            }
        }
        return out.toList()
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
        val stopped = when (backend) {
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
        // Tunnel down must not open the net while kill-switch is preferred.
        if (readKillSwitchEnabled()) ensureKillSwitch()
        return stopped
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
            if (readKillSwitchEnabled()) ensureKillSwitch()
            return true
        }
        val marker = torConfigFile().absolutePath
        Sys.run("pkill", "-f", marker, timeoutSec = 4)
        torPidFile().delete()
        TelosSessionAuditLog.append("network_policy", "tor_stop")
        if (readKillSwitchEnabled()) ensureKillSwitch()
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

    fun defaultDnsBlocklistTemplate(): String =
        """
            # Telos DNS blocklist — one hostname/domain per line
            # Persisted under vault ~/.config/telos/dns-block.txt
            # Linux: enforced when the Telos DNS stub is running (dns-stub.port and/or
            # dns-stub.enabled) and nftables redirects DNS to 127.0.0.1, and/or with a
            # NextDNS config ID via resolvectl DoT. Without those paths, preference-only.
            # Windows/macOS: preference-only.
        """.trimIndent() + "\n"

    fun defaultDnsAllowlistTemplate(): String =
        """
            # Telos DNS allowlist — exceptions over dns-block.txt (one domain per line)
            # Persisted under vault ~/.config/telos/dns-allow.txt
            # Same enforcement rules as the blocklist (no silent fake filtering).
        """.trimIndent() + "\n"

    fun readDnsBlocklistText(): String {
        val file = dnsBlocklistFile()
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(defaultDnsBlocklistTemplate())
        }
        return file.readText()
    }

    fun writeDnsBlocklistText(text: String) {
        dnsBlocklistFile().apply { parentFile?.mkdirs() }.writeText(text)
        TelosSessionAuditLog.append("network_policy", "dns_blocklist_updated")
    }

    fun readDnsAllowlistText(): String {
        val file = dnsAllowlistFile()
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(defaultDnsAllowlistTemplate())
        }
        return file.readText()
    }

    fun writeDnsAllowlistText(text: String) {
        dnsAllowlistFile().apply { parentFile?.mkdirs() }.writeText(text)
        TelosSessionAuditLog.append("network_policy", "dns_allowlist_updated")
    }

    fun parseDnsDomainLines(lines: List<String>): List<String> =
        lines.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                var host = line.substringBefore('#').trim().trimEnd('.')
                val fromUrl = host.startsWith("http://") || host.startsWith("https://")
                when {
                    host.startsWith("http://") -> host = host.removePrefix("http://")
                    host.startsWith("https://") -> host = host.removePrefix("https://")
                }
                if (host.contains('/')) {
                    // Allow a single path only when the line was a full URL; reject evil/path.
                    if (!fromUrl) return@mapNotNull null
                    val pathIdx = host.indexOf('/')
                    if (pathIdx <= 0) return@mapNotNull null
                    host = host.substring(0, pathIdx)
                }
                if (host.contains('\\') || host.contains(' ')) return@mapNotNull null
                host = host.substringBefore(':').trimEnd('.')
                if (host.isEmpty() || !DNS_DOMAIN_LINE.matches(host)) return@mapNotNull null
                host
            }
            .distinct()

    fun readDnsBlocklist(): List<String> = parseDnsDomainLines(readDnsBlocklistText().lines())

    fun readDnsAllowlist(): List<String> = parseDnsDomainLines(readDnsAllowlistText().lines())

    fun isValidNextDnsConfigId(id: String): Boolean = NEXTDNS_ID.matches(id.trim())

    fun readNextDnsConfigId(): String? {
        val file = nextDnsConfigIdFile()
        if (!file.isFile) return null
        val line = file.readLines().map { it.trim() }.firstOrNull {
            it.isNotEmpty() && !it.startsWith("#")
        } ?: return null
        val id = line.substringBefore('#').trim()
        return id.takeIf { isValidNextDnsConfigId(it) }
    }

    fun writeNextDnsConfigId(id: String?) {
        val trimmed = id?.trim().orEmpty()
        nextDnsConfigIdFile().apply { parentFile?.mkdirs() }.writeText(
            buildString {
                appendLine("# Telos NextDNS configuration ID (4–12 letters/digits)")
                appendLine("# When set on Linux with resolvectl, Telos applies DoT to")
                appendLine("# <id>.dns.nextdns.io. Without resolvectl/nft privilege this")
                appendLine("# stays preference-only (no fake local filtering).")
                if (trimmed.isNotEmpty()) appendLine(trimmed)
            },
        )
        TelosSessionAuditLog.append(
            "network_policy",
            if (trimmed.isEmpty()) "nextdns_id_cleared" else "nextdns_id_updated",
        )
    }

    fun readDnsStubPort(): Int? {
        val file = dnsStubPortFile()
        if (!file.isFile) return null
        val line = file.readLines().map { it.trim() }.firstOrNull {
            it.isNotEmpty() && !it.startsWith("#")
        } ?: return null
        val port = line.substringBefore('#').trim().toIntOrNull() ?: return null
        return port.takeIf { it in 1..65535 }
    }

    fun writeDnsStubPort(port: Int?) {
        dnsStubPortFile().apply { parentFile?.mkdirs() }.writeText(
            buildString {
                appendLine("# Telos local DNS stub listen port (127.0.0.1)")
                appendLine("# Used with nftables inet telos_dns redirect of outbound :53.")
                if (port != null) appendLine(port.coerceIn(1, 65535).toString())
            },
        )
    }

    fun readDnsStubEnabled(): Boolean {
        val file = dnsStubEnabledFile()
        if (!file.isFile) return false
        val line = file.readLines().map { it.trim() }.firstOrNull {
            it.isNotEmpty() && !it.startsWith("#")
        } ?: return false
        return line.equals("true", ignoreCase = true) ||
            line.equals("on", ignoreCase = true) ||
            line == "1"
    }

    fun writeDnsStubEnabled(enabled: Boolean) {
        dnsStubEnabledFile().apply { parentFile?.mkdirs() }.writeText(
            buildString {
                appendLine("# Telos DNS stub: true|false")
                appendLine("# When true (Linux), Telos starts a 127.0.0.1 UDP/TCP forwarder")
                appendLine("# that NXDOMAINs dns-block.txt (allowlist wins) and redirects via nft.")
                appendLine(if (enabled) "true" else "false")
            },
        )
        TelosSessionAuditLog.append(
            "network_policy",
            if (enabled) "dns_stub_enabled" else "dns_stub_disabled",
        )
    }

    /** Stub wanted when the port file is set or the user enabled "Telos DNS stub". */
    fun dnsStubWanted(): Boolean = readDnsStubEnabled() || readDnsStubPort() != null

    fun ensureDnsStubPort(): Int {
        readDnsStubPort()?.let { return it }
        writeDnsStubPort(TelosDnsStub.DEFAULT_PORT)
        return TelosDnsStub.DEFAULT_PORT
    }

    fun nextDnsDotHost(id: String): String = "${id.trim()}.dns.nextdns.io"

    fun nextDnsDotPreferenceLine(id: String): String = "tls://${nextDnsDotHost(id)}"

    /**
     * Apply NextDNS-class filter foundations:
     * - Linux + valid NextDNS ID + resolvectl → DoT to NextDNS.
     * - Linux + (dns-stub.port OR dns-stub.enabled) → start local stub + nft redirect;
     *   host lists alone become Enforced when both succeed.
     * - Otherwise PreferenceOnly.
     */
    fun applyDnsFilters(): DnsFilterStatus {
        val id = readNextDnsConfigId()
        val rawId = nextDnsConfigIdFile().takeIf { it.isFile }?.readLines()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?.substringBefore('#')
            ?.trim()
        if (!rawId.isNullOrEmpty() && id == null) return DnsFilterStatus.Invalid

        val hasLists = readDnsBlocklist().isNotEmpty() || readDnsAllowlist().isNotEmpty()
        val stubWanted = dnsStubWanted()
        val configured = id != null || hasLists || stubWanted

        if (!isLinux) {
            TelosDnsStub.stop()
            return if (configured) DnsFilterStatus.PreferenceOnly else DnsFilterStatus.Disabled
        }

        var enforced = false
        var failed = false

        if (id != null) {
            if (Sys.which("resolvectl") == null) {
                // Config-only when privileged resolver tooling is missing.
            } else {
                writeDnsPreference(nextDnsDotPreferenceLine(id))
                when (applyDnsPreference()) {
                    DnsApplyResult.Applied -> {
                        enforced = true
                        TelosSessionAuditLog.append("network_policy", "nextdns_dot_applied")
                    }
                    DnsApplyResult.PreferenceOnly -> Unit
                    DnsApplyResult.Failed -> failed = true
                    DnsApplyResult.Invalid -> return DnsFilterStatus.Invalid
                }
            }
        }

        when (applyLocalDnsStubAndRedirect()) {
            DnsApplyResult.Applied -> enforced = true
            DnsApplyResult.Failed -> failed = true
            DnsApplyResult.Invalid -> return DnsFilterStatus.Invalid
            DnsApplyResult.PreferenceOnly -> Unit
        }

        return when {
            failed && !enforced -> DnsFilterStatus.Failed
            enforced -> DnsFilterStatus.Enforced
            configured -> DnsFilterStatus.PreferenceOnly
            else -> DnsFilterStatus.Disabled
        }
    }

    fun dnsFilterStatus(): DnsFilterStatus = dnsFilterSnapshot().status

    fun dnsFilterSnapshot(): DnsFilterSnapshot {
        val id = readNextDnsConfigId()
        val rawId = nextDnsConfigIdFile().takeIf { it.isFile }?.readLines()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?.substringBefore('#')
            ?.trim()
        val invalidId = !rawId.isNullOrEmpty() && id == null
        val block = readDnsBlocklist()
        val allow = readDnsAllowlist()
        val stub = readDnsStubPort() ?: TelosDnsStub.listeningPort()
        val stubWanted = dnsStubWanted()
        val stubRunning = TelosDnsStub.isRunning()
        val redirectOk = stub != null && localDnsRedirectActive(stub)
        val status = when {
            invalidId -> DnsFilterStatus.Invalid
            !isLinux -> {
                if (id != null || block.isNotEmpty() || allow.isNotEmpty() || stubWanted) {
                    DnsFilterStatus.PreferenceOnly
                } else {
                    DnsFilterStatus.Disabled
                }
            }
            id != null && Sys.which("resolvectl") != null && nextDnsResolvectlActive(id) ->
                DnsFilterStatus.Enforced
            stubRunning && redirectOk -> DnsFilterStatus.Enforced
            stubWanted && (!stubRunning || !redirectOk) -> DnsFilterStatus.Failed
            id != null || block.isNotEmpty() || allow.isNotEmpty() || stubWanted ->
                DnsFilterStatus.PreferenceOnly
            else -> DnsFilterStatus.Disabled
        }
        return DnsFilterSnapshot(block, allow, id, stub, status)
    }

    /**
     * Start the local DNS stub when port/enabled is set, then redirect outbound DNS (53)
     * via nftables NAT. Host lists become Enforced only when both bind and nft succeed.
     */
    fun applyLocalDnsStubAndRedirect(): DnsApplyResult {
        if (!dnsStubWanted()) {
            TelosDnsStub.stop()
            return DnsApplyResult.PreferenceOnly
        }
        if (!isLinux) {
            TelosDnsStub.stop()
            return DnsApplyResult.PreferenceOnly
        }
        val port = ensureDnsStubPort()
        val started = TelosDnsStub.start(
            port = port,
            blocklist = readDnsBlocklist(),
            allowlist = readDnsAllowlist(),
            upstream = TelosDnsStub.resolveDefaultUpstream(),
        )
        if (!started) {
            TelosSessionAuditLog.append("network_policy", "dns_stub_bind_failed")
            return DnsApplyResult.Failed
        }
        TelosSessionAuditLog.append("network_policy", "dns_stub_started")
        return applyLocalDnsRedirect(port)
    }

    fun applyLocalDnsRedirectIfConfigured(): DnsApplyResult = applyLocalDnsStubAndRedirect()

    fun applyLocalDnsRedirect(port: Int = readDnsStubPort() ?: TelosDnsStub.DEFAULT_PORT): DnsApplyResult {
        if (!isLinux || !PlatformEnvironment.settings.showNftSessionNetGuard) {
            return DnsApplyResult.PreferenceOnly
        }
        if (Sys.which("nft") == null) return DnsApplyResult.Failed
        val script = File.createTempFile("telos-dns-nft", ".nft")
        script.writeText(dnsRedirectNftRules(port))
        runCatching {
            script.setReadable(false, false)
            script.setWritable(false, false)
            script.setReadable(true, true)
            script.setWritable(true, true)
        }
        val r = Sys.run("pkexec", "nft", "-f", script.absolutePath, timeoutSec = 20)
        val ok = r.ok || Sys.run("nft", "-f", script.absolutePath, timeoutSec = 10).ok
        script.delete()
        TelosSessionAuditLog.append(
            "network_policy",
            if (ok) "dns_stub_redirect_applied" else "dns_stub_redirect_failed",
        )
        return if (ok) DnsApplyResult.Applied else DnsApplyResult.Failed
    }

    fun clearLocalDnsRedirect() {
        if (!isLinux) return
        TelosDnsStub.stop()
        Sys.run("pkexec", "nft", "delete", "table", "inet", "telos_dns", timeoutSec = 10)
        Sys.run("nft", "delete", "table", "inet", "telos_dns", timeoutSec = 8)
    }

    fun setDnsStubEnabled(enabled: Boolean): DnsFilterStatus {
        writeDnsStubEnabled(enabled)
        if (enabled) {
            ensureDnsStubPort()
            return applyDnsFilters()
        }
        if (readDnsStubPort() == null) {
            clearLocalDnsRedirect()
            return DnsFilterStatus.Disabled
        }
        return applyDnsFilters()
    }

    internal fun dnsRedirectNftRules(port: Int): String =
        """
            destroy table inet telos_dns
            table inet telos_dns {
              chain output {
                type nat hook output priority -100; policy accept;
                meta l4proto { tcp, udp } th dport 53 redirect to :$port
              }
            }
        """.trimIndent()

    private fun nextDnsResolvectlActive(id: String): Boolean {
        if (Sys.which("resolvectl") == null) return false
        val status = Sys.run("resolvectl", "status", timeoutSec = 6)
        if (!status.ok) return false
        val host = nextDnsDotHost(id)
        return status.stdout.contains(host, ignoreCase = true)
    }

    private fun localDnsRedirectActive(port: Int): Boolean {
        if (!isLinux) return false
        val r = Sys.run("nft", "list", "table", "inet", "telos_dns", timeoutSec = 6)
        return r.ok && r.stdout.contains("telos_dns") && r.stdout.contains(":$port")
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
