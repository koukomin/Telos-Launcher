package de.mm20.launcher2.network.api

import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.StateFlow

/**
 * Firewall rules of one app. All flags default to "not blocked". An app without an entry has no rules.
 * Rules are stored per app id ([FlowInfo.appId]).
 */
@Serializable
data class AppRule(
    val appId: Int,
    /** Block every connection of the app. */
    val blockAll: Boolean = false,
    /** Block connections over wifi. */
    val blockWifi: Boolean = false,
    /** Block connections over mobile data. */
    val blockMobile: Boolean = false,
    /** Block connections over mobile data while roaming. */
    val blockRoaming: Boolean = false,
    /** Block connections to the local network (private and link-local addresses). */
    val blockLan: Boolean = false,
    /** Block connections while another VPN is the underlying network. */
    val blockVpn: Boolean = false,
    /** Block connections while the app is in the background (only effective when the foreground state is known). */
    val blockBackground: Boolean = false,
    /** Block connections while the screen is off. */
    val blockScreenOff: Boolean = false,
    /** The universal rules do not apply to this app. App rules, IP and domain rules still do. */
    val ignoreUniversalRules: Boolean = false,
    /** The app skips everything, including blocklists and IP/domain rules. DNS is still answered by Telos. */
    val bypassFirewall: Boolean = false,
    /** The app does not use the VPN at all; its traffic leaves the device directly. Applies after the VPN restarted. */
    val excludeFromVpn: Boolean = false,
) {
    /** True when the entry has no effect and can be deleted. */
    val isEmpty: Boolean
        get() = this == AppRule(appId)
}

/** Rules that apply to all apps unless an app is [AppRule.ignoreUniversalRules] or [AppRule.bypassFirewall]. */
@Serializable
data class UniversalRules(
    val blockWifi: Boolean = false,
    val blockMobile: Boolean = false,
    val blockRoaming: Boolean = false,
    val blockMetered: Boolean = false,
    val blockLan: Boolean = false,
    val blockBackground: Boolean = false,
    val blockScreenOff: Boolean = false,
    val blockWhenDeviceLocked: Boolean = false,
    /** Block apps that were installed after this rule was switched on until the user allows them. */
    val blockNewApps: Boolean = false,
    /** Block connections whose owner app could not be determined. */
    val blockUnknownApps: Boolean = false,
    val blockUdp: Boolean = false,
    /** Block unencrypted HTTP (port 80). */
    val blockHttp: Boolean = false,
    /** Block apps that use their own DNS (port 53, 853 to anywhere but Telos). */
    val blockDnsBypass: Boolean = false,
    /** Allow nothing except what is explicitly allowed by rules (default deny). */
    val defaultDeny: Boolean = false,
)

/** What an IP or domain rule does. */
enum class RuleAction {
    /** Block matching connections or queries. */
    Block,

    /** Allow matching connections or queries and skip the universal rules and blocklists for them. */
    Trust,
}

/**
 * Rule for an IP address or range.
 * [address] is an IPv4/IPv6 literal or CIDR (`10.0.0.0/8`). [port] 0 means any port.
 */
@Serializable
data class IpRule(
    val id: Long,
    val scope: RuleScope,
    val address: String,
    val port: Int = 0,
    /** `null` = any protocol. */
    val protocol: Protocol? = null,
    val action: RuleAction,
    val createdAtMs: Long = System.currentTimeMillis(),
)

/**
 * Rule for a domain. [domain] is an exact name (`example.com`) or a wildcard (`*.example.com`, which
 * matches the domain and all its subdomains).
 */
@Serializable
data class DomainRule(
    val id: Long,
    val scope: RuleScope,
    val domain: String,
    val action: RuleAction,
    val createdAtMs: Long = System.currentTimeMillis(),
)

/**
 * The firewall: per-app rules, universal rules, and IP/domain rules. [decide] and [decideDns] are what
 * the engine calls for every connection and every DNS question.
 *
 * Evaluation order for [decide], first match wins:
 *  1. Telos itself and uid-less internal traffic: allow ([DecisionReason.Internal]).
 *  2. App has `bypassFirewall`: allow.
 *  3. IP rules and domain rules (app scope before system scope; `Trust` before `Block`).
 *  4. App rules (blockAll, connection types, background, screen off).
 *  5. Universal rules unless the app ignores them.
 *  6. Blocklist hits reported by the DNS layer ([FlowInfo.blocklists], [FlowInfo.dnsBlocked]).
 *  7. Allow.
 * Implementations may refine this but must keep `bypassFirewall` and Trust semantics.
 */
interface FirewallController {
    /** All app rules, keyed by app id. */
    val appRules: StateFlow<Map<Int, AppRule>>

    /** Rules for all apps. */
    val universalRules: StateFlow<UniversalRules>

    /** All IP rules. */
    val ipRules: StateFlow<List<IpRule>>

    /** All domain rules. */
    val domainRules: StateFlow<List<DomainRule>>

    /** Saves the rules of an app. An [AppRule.isEmpty] rule removes the entry. */
    suspend fun setAppRule(rule: AppRule)

    /** Removes all rules of one app (app rule only, not its IP/domain rules). */
    suspend fun clearAppRule(appId: Int)

    /** Changes the universal rules. */
    suspend fun setUniversalRules(rules: UniversalRules)

    /** Adds an IP rule; the id of [rule] is ignored. Fails when [IpRule.address] does not parse. */
    suspend fun addIpRule(rule: IpRule): Result<IpRule>

    suspend fun removeIpRule(id: Long)

    /** Adds a domain rule; the id of [rule] is ignored. Fails for an empty or invalid domain. */
    suspend fun addDomainRule(rule: DomainRule): Result<DomainRule>

    suspend fun removeDomainRule(id: Long)

    /** Deletes every rule, app rule and universal rule. */
    suspend fun resetAll()

    /**
     * Decides about a new connection. Called on engine threads for every flow, so it must not block
     * or touch disk: work on in-memory snapshots. Never throw; the engine treats an exception as
     * allow ([DecisionReason.EngineError]) so a bug cannot cut the internet.
     */
    fun decide(flow: FlowInfo): FlowDecision

    /**
     * Decides about a DNS question: [DnsVerdict.Block] for domains with a Block rule (or app blockAll),
     * [DnsVerdict.AllowSkipBlocklists] for trusted domains (domain Trust rule or a
     * [BlocklistController] bypass). Same threading rules as [decide].
     */
    fun decideDns(query: DnsQuery): DnsVerdict
}
