package de.mm20.launcher2.network.api

import java.net.InetAddress

/**
 * Shared vocabulary of the Telos Network API. Everything here is plain Kotlin and has no dependency
 * on the engine, so the UI can use it freely.
 */

/** Which IP families go through the tunnel. */
enum class IpMode {
    /** Only IPv4 is routed into the tunnel, IPv6 bypasses it. */
    V4,

    /** Only IPv6 is routed into the tunnel, IPv4 bypasses it. */
    V6,

    /** Both families. The default. */
    V46,
}

/** Transport protocol of a connection. */
enum class Protocol(val number: Int) {
    Tcp(6),
    Udp(17),
    Icmp(1),
    Icmp6(58),
    Other(-1);

    companion object {
        /** Maps an IP protocol number (6, 17, 1, 58) to a [Protocol]; everything else is [Other]. */
        fun fromNumber(number: Int): Protocol = entries.firstOrNull { it.number == number } ?: Other
    }
}

/** The kind of network a connection leaves the device on. */
enum class ConnectionType {
    Wifi,
    Mobile,
    Ethernet,

    /** The underlying network is itself a VPN. */
    Vpn,
    Other,

    /** No usable network at the moment. */
    None,
}

/**
 * The state of the device at the moment a connection is opened. The engine fills this in for every
 * flow so that [FirewallController] never has to talk to Android itself.
 */
data class FlowEnvironment(
    /** Network the connection will use. */
    val network: ConnectionType = ConnectionType.Other,
    /** True when [network] is [ConnectionType.Mobile] and the device is roaming. */
    val roaming: Boolean = false,
    /** True when the network is metered. */
    val metered: Boolean = false,
    /** True when the screen is on. */
    val screenOn: Boolean = true,
    /** True when the keyguard is showing. */
    val deviceLocked: Boolean = false,
    /**
     * Whether the app owning the connection is in the foreground. `null` means unknown; the
     * engine currently cannot know this for other apps, so rules treat `null` as foreground and
     * never block "background" connections on it.
     */
    val appInForeground: Boolean? = null,
)

/**
 * A connection (TCP, UDP or ICMP flow) that an app wants to open, as seen by the engine.
 * [FirewallController.decide] receives one of these for every new flow.
 */
data class FlowInfo(
    /** Unique id of this flow, also used to match the log entry and the final byte counts. */
    val flowId: String,
    /** Android uid of the app, including the user id (`userId * 100000 + appId`). -1 when unknown. */
    val uid: Int,
    val protocol: Protocol,
    val sourceIp: String,
    val sourcePort: Int,
    /** The real destination IP (not the fake address that the DNS layer may hand to apps). */
    val destIp: String,
    val destPort: Int,
    /** Domain the app resolved to get [destIp], when the DNS layer knows it. */
    val domain: String? = null,
    /** Names/ids of the blocklists the DNS layer matched [domain] with. Empty when none. */
    val blocklists: List<String> = emptyList(),
    /** True when the DNS layer already blocked the name (the app got an unspecified address). */
    val dnsBlocked: Boolean = false,
    /** True for a connection coming from the outside into the device. */
    val incoming: Boolean = false,
    val environment: FlowEnvironment = FlowEnvironment(),
    /** Epoch milliseconds when the flow was seen. */
    val timestampMs: Long = System.currentTimeMillis(),
) {
    /** The uid without the user id, the key under which rules are stored. */
    val appId: Int get() = if (uid < 0) uid else uid % PER_USER_RANGE

    /** True when [destIp] is a loopback, link-local or private (LAN) address. */
    val destIsLan: Boolean get() = isLanAddress(destIp)

    companion object {
        /** Android gives every user a range of this many uids. */
        const val PER_USER_RANGE = 100000

        /** Uid used when the owner of a connection could not be determined. */
        const val UNKNOWN_UID = -1
    }
}

/** True for loopback, link-local, site-local and unique-local addresses (IPv4 and IPv6) given as literals. */
fun isLanAddress(ip: String): Boolean {
    val address = try {
        // Only literals are ever passed in, so this never does a DNS lookup
        InetAddress.getByName(ip.trim().removePrefix("[").removeSuffix("]"))
    } catch (e: Exception) {
        return false
    }
    if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true
    val bytes = address.address
    // fc00::/7 unique local addresses are not covered by isSiteLocalAddress
    return bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
}

/** The final answer of the firewall for a connection. */
enum class Verdict { Allow, Block }

/** Why a connection or DNS query was allowed or blocked. Shown in the logs. */
enum class DecisionReason {
    /** No rule matched, the default applies. */
    Default,
    /** Rule of the app: blocked entirely, or exempted from everything. */
    AppRule,
    /** Rule of the app for one connection type (wifi, mobile, roaming, lan, vpn). */
    ConnectionTypeRule,
    /** Rule of the app for background connections. */
    BackgroundRule,
    /** Rule of the app for connections while the screen is off. */
    ScreenOffRule,
    /** One of the rules that apply to all apps. */
    UniversalRule,
    /** A domain rule (per app or system-wide). */
    DomainRule,
    /** An IP rule (per app or system-wide). */
    IpRule,
    /** The domain is on an enabled blocklist. */
    Blocklist,
    /** The user explicitly trusted this domain or IP, which skips blocklists. */
    Trusted,
    /** The connection belongs to Telos itself or to the system and is never filtered. */
    Internal,
    /** The firewall failed to decide; the connection is let through (fail-open). */
    EngineError,
}

/** The answer of the firewall for one connection. */
data class FlowDecision(
    val verdict: Verdict,
    val reason: DecisionReason = DecisionReason.Default,
    /** Id of the rule that decided, when it was an IP or domain rule. */
    val ruleId: Long? = null,
) {
    val blocked: Boolean get() = verdict == Verdict.Block

    companion object {
        val Allowed = FlowDecision(Verdict.Allow)
        fun allow(reason: DecisionReason, ruleId: Long? = null) = FlowDecision(Verdict.Allow, reason, ruleId)
        fun block(reason: DecisionReason, ruleId: Long? = null) = FlowDecision(Verdict.Block, reason, ruleId)
    }
}

/** A DNS question asked by an app. */
data class DnsQuery(
    /** Android uid of the asking app including the user id, -1 when unknown. */
    val uid: Int,
    /** The name without trailing dot. */
    val domain: String,
    /** Numeric record type: 1 = A, 28 = AAAA, 5 = CNAME, 65 = HTTPS... */
    val type: Int,
)

/** What to do with a DNS question. */
enum class DnsVerdict {
    /** Resolve normally, blocklists apply. */
    Allow,

    /** Answer with "blocked" without asking the upstream. */
    Block,

    /** Resolve and skip the blocklists (the user trusts this name). */
    AllowSkipBlocklists,
}

/** Scope of a rule: one app, or all apps together. */
sealed interface RuleScope {
    /** Applies to every app. */
    data object System : RuleScope

    /** Applies to one app, given by its uid without user id ([FlowInfo.appId]). */
    data class App(val appId: Int) : RuleScope
}

/** How much the notification of the running VPN shows. */
enum class NotificationDetail {
    /** Only "Telos Network is on" and the stop button. */
    Minimal,

    /** Also shows the number of blocked connections. */
    WithCounters,
}
