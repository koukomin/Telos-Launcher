package de.mm20.launcher2.network.impl

import android.content.Context
import de.mm20.launcher2.network.api.AppRule
import de.mm20.launcher2.network.api.BlocklistController
import de.mm20.launcher2.network.api.ConnectionType
import de.mm20.launcher2.network.api.DecisionReason
import de.mm20.launcher2.network.api.DnsQuery
import de.mm20.launcher2.network.api.DnsVerdict
import de.mm20.launcher2.network.api.DomainRule
import de.mm20.launcher2.network.api.FirewallController
import de.mm20.launcher2.network.api.FlowDecision
import de.mm20.launcher2.network.api.FlowInfo
import de.mm20.launcher2.network.api.IpRule
import de.mm20.launcher2.network.api.Protocol
import de.mm20.launcher2.network.api.RuleAction
import de.mm20.launcher2.network.api.RuleScope
import de.mm20.launcher2.network.api.UniversalRules
import de.mm20.launcher2.network.util.IpUtil
import de.mm20.launcher2.network.util.PersistedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
internal data class FirewallState(
    val appRules: List<AppRule> = emptyList(),
    val universal: UniversalRules = UniversalRules(),
    val ipRules: List<IpRule> = emptyList(),
    val domainRules: List<DomainRule> = emptyList(),
    val nextId: Long = 1,
)

/**
 * Default firewall: keeps all rules in memory (and in `filesDir/network/firewall.json`) and
 * evaluates them in the order documented on [FirewallController]. The rule sets are compiled
 * into immutable snapshots on every change, so [decide] never allocates much and never blocks.
 */
internal class DefaultFirewallController(
    context: Context,
    private val blocklists: BlocklistController,
) : FirewallController {
    private val store = PersistedState(
        file = File(File(context.filesDir, "network"), "firewall.json"),
        serializer = FirewallState.serializer(),
        default = { FirewallState() },
    )

    private class CompiledIp(val rule: IpRule, val cidr: IpUtil.Cidr)

    private class Compiled(
        val appRules: Map<Int, AppRule>,
        val universal: UniversalRules,
        val ip: List<CompiledIp>,
        val domains: List<DomainRule>,
    )

    @Volatile
    private var compiled: Compiled = compile(store.value)

    private val _appRules = MutableStateFlow(store.value.appRules.associateBy { it.appId })
    override val appRules: StateFlow<Map<Int, AppRule>> = _appRules

    private val _universal = MutableStateFlow(store.value.universal)
    override val universalRules: StateFlow<UniversalRules> = _universal

    private val _ipRules = MutableStateFlow(store.value.ipRules)
    override val ipRules: StateFlow<List<IpRule>> = _ipRules

    private val _domainRules = MutableStateFlow(store.value.domainRules)
    override val domainRules: StateFlow<List<DomainRule>> = _domainRules

    private fun compile(state: FirewallState) = Compiled(
        appRules = state.appRules.associateBy { it.appId },
        universal = state.universal,
        ip = state.ipRules.mapNotNull { r -> IpUtil.parseCidr(r.address)?.let { CompiledIp(r, it) } },
        domains = state.domainRules,
    )

    private fun commit(transform: (FirewallState) -> FirewallState): FirewallState {
        val state = store.update(transform)
        compiled = compile(state)
        _appRules.value = compiled.appRules
        _universal.value = state.universal
        _ipRules.value = state.ipRules
        _domainRules.value = state.domainRules
        return state
    }

    override suspend fun setAppRule(rule: AppRule) {
        commit { s ->
            val others = s.appRules.filterNot { it.appId == rule.appId }
            s.copy(appRules = if (rule.isEmpty) others else others + rule)
        }
    }

    override suspend fun clearAppRule(appId: Int) {
        commit { s -> s.copy(appRules = s.appRules.filterNot { it.appId == appId }) }
    }

    override suspend fun setUniversalRules(rules: UniversalRules) {
        commit { it.copy(universal = rules) }
    }

    override suspend fun addIpRule(rule: IpRule): Result<IpRule> {
        IpUtil.parseCidr(rule.address)
            ?: return Result.failure(IllegalArgumentException("invalid address: ${rule.address}"))
        if (rule.port !in 0..65535) return Result.failure(IllegalArgumentException("invalid port"))
        var created: IpRule? = null
        commit { s ->
            val r = rule.copy(id = s.nextId, address = rule.address.trim(), createdAtMs = System.currentTimeMillis())
            created = r
            s.copy(ipRules = s.ipRules + r, nextId = s.nextId + 1)
        }
        return Result.success(created!!)
    }

    override suspend fun removeIpRule(id: Long) {
        commit { s -> s.copy(ipRules = s.ipRules.filterNot { it.id == id }) }
    }

    override suspend fun addDomainRule(rule: DomainRule): Result<DomainRule> {
        val d = IpUtil.normalizeDomain(rule.domain)
        val body = d.removePrefix("*.")
        if (body.isEmpty() || body.contains(' ') || body.contains('/') || body.contains('*')) {
            return Result.failure(IllegalArgumentException("invalid domain: ${rule.domain}"))
        }
        var created: DomainRule? = null
        commit { s ->
            val r = rule.copy(id = s.nextId, domain = d, createdAtMs = System.currentTimeMillis())
            created = r
            s.copy(domainRules = s.domainRules + r, nextId = s.nextId + 1)
        }
        return Result.success(created!!)
    }

    override suspend fun removeDomainRule(id: Long) {
        commit { s -> s.copy(domainRules = s.domainRules.filterNot { it.id == id }) }
    }

    override suspend fun resetAll() {
        commit { FirewallState() }
    }

    // --- decisions ---

    override fun decide(flow: FlowInfo): FlowDecision {
        val c = compiled
        val appId = flow.appId
        val app = c.appRules[appId]

        if (app?.bypassFirewall == true) return FlowDecision.allow(DecisionReason.AppRule)

        // IP and domain rules
        matchIpRule(c, flow)?.let { rule ->
            return when (rule.action) {
                RuleAction.Trust -> FlowDecision.allow(DecisionReason.IpRule, rule.id)
                RuleAction.Block -> FlowDecision.block(DecisionReason.IpRule, rule.id)
            }
        }
        flow.domain?.takeIf { it.isNotBlank() }?.let { domain ->
            matchDomainRule(c, appId, IpUtil.normalizeDomain(domain))?.let { rule ->
                return when (rule.action) {
                    RuleAction.Trust -> FlowDecision.allow(DecisionReason.DomainRule, rule.id)
                    RuleAction.Block -> FlowDecision.block(DecisionReason.DomainRule, rule.id)
                }
            }
        }

        // app rules
        if (app != null) {
            if (app.blockAll) return FlowDecision.block(DecisionReason.AppRule)
            val env = flow.environment
            if (app.blockWifi && env.network == ConnectionType.Wifi) return FlowDecision.block(DecisionReason.ConnectionTypeRule)
            if (app.blockMobile && env.network == ConnectionType.Mobile) return FlowDecision.block(DecisionReason.ConnectionTypeRule)
            if (app.blockRoaming && env.roaming) return FlowDecision.block(DecisionReason.ConnectionTypeRule)
            if (app.blockVpn && env.network == ConnectionType.Vpn) return FlowDecision.block(DecisionReason.ConnectionTypeRule)
            if (app.blockLan && flow.destIsLan) return FlowDecision.block(DecisionReason.ConnectionTypeRule)
            if (app.blockBackground && env.appInForeground == false) return FlowDecision.block(DecisionReason.BackgroundRule)
            if (app.blockScreenOff && !env.screenOn) return FlowDecision.block(DecisionReason.ScreenOffRule)
        }

        // universal rules
        if (app?.ignoreUniversalRules != true) {
            universalBlock(c.universal, flow)?.let { return it }
        }

        // blocklist hits reported by the DNS layer
        if (flow.dnsBlocked || flow.blocklists.isNotEmpty()) {
            val domain = flow.domain
            val trusted = domain != null && blocklists.isBypassed(flow.uid, domain)
            if (!trusted) return FlowDecision.block(DecisionReason.Blocklist)
            return FlowDecision.allow(DecisionReason.Trusted)
        }

        if (c.universal.defaultDeny && app?.ignoreUniversalRules != true) {
            return FlowDecision.block(DecisionReason.UniversalRule)
        }
        return FlowDecision.Allowed
    }

    private fun universalBlock(u: UniversalRules, flow: FlowInfo): FlowDecision? {
        val env = flow.environment
        val reason = DecisionReason.UniversalRule
        if (u.blockWifi && env.network == ConnectionType.Wifi) return FlowDecision.block(reason)
        if (u.blockMobile && env.network == ConnectionType.Mobile) return FlowDecision.block(reason)
        if (u.blockRoaming && env.roaming) return FlowDecision.block(reason)
        if (u.blockMetered && env.metered) return FlowDecision.block(reason)
        if (u.blockLan && flow.destIsLan) return FlowDecision.block(reason)
        if (u.blockBackground && env.appInForeground == false) return FlowDecision.block(reason)
        if (u.blockScreenOff && !env.screenOn) return FlowDecision.block(reason)
        if (u.blockWhenDeviceLocked && env.deviceLocked) return FlowDecision.block(reason)
        if (u.blockUnknownApps && flow.uid < 0) return FlowDecision.block(reason)
        if (u.blockUdp && flow.protocol == Protocol.Udp) return FlowDecision.block(reason)
        if (u.blockHttp && flow.protocol == Protocol.Tcp && flow.destPort == 80) return FlowDecision.block(reason)
        if (u.blockDnsBypass && (flow.destPort == 53 || flow.destPort == 853) && !flow.destIsLan) return FlowDecision.block(reason)
        return null
    }

    private fun matchIpRule(c: Compiled, flow: FlowInfo): IpRule? {
        if (c.ip.isEmpty()) return null
        val address = IpUtil.parse(flow.destIp) ?: return null
        val matches = c.ip.filter { ci ->
            val r = ci.rule
            val scopeOk = when (val s = r.scope) {
                RuleScope.System -> true
                is RuleScope.App -> s.appId == flow.appId
            }
            scopeOk &&
                (r.port == 0 || r.port == flow.destPort) &&
                (r.protocol == null || r.protocol == flow.protocol) &&
                ci.cidr.contains(address)
        }.map { it.rule }
        return pick(matches, { it.scope }, { it.action })
    }

    private fun matchDomainRule(c: Compiled, appId: Int, domain: String): DomainRule? {
        if (c.domains.isEmpty()) return null
        val matches = c.domains.filter { r ->
            val scopeOk = when (val s = r.scope) {
                RuleScope.System -> true
                is RuleScope.App -> s.appId == appId
            }
            scopeOk && DefaultBlocklistController.domainMatches(r.domain, domain)
        }
        return pick(matches, { it.scope }, { it.action })
    }

    /** App scope before system scope, Trust before Block. */
    private fun <T> pick(list: List<T>, scope: (T) -> RuleScope, action: (T) -> RuleAction): T? {
        if (list.isEmpty()) return null
        return list.minWithOrNull(compareBy<T>({ if (scope(it) is RuleScope.App) 0 else 1 }, { if (action(it) == RuleAction.Trust) 0 else 1 }))
    }

    override fun decideDns(query: DnsQuery): DnsVerdict {
        val c = compiled
        val appId = if (query.uid < 0) -1 else query.uid % FlowInfo.PER_USER_RANGE
        val app = c.appRules[appId]
        if (app?.bypassFirewall == true) return DnsVerdict.AllowSkipBlocklists
        val domain = IpUtil.normalizeDomain(query.domain)
        matchDomainRule(c, appId, domain)?.let { rule ->
            return if (rule.action == RuleAction.Trust) DnsVerdict.AllowSkipBlocklists else DnsVerdict.Block
        }
        if (app?.blockAll == true) return DnsVerdict.Block
        if (blocklists.isBypassed(query.uid, domain)) return DnsVerdict.AllowSkipBlocklists
        return DnsVerdict.Allow
    }
}
