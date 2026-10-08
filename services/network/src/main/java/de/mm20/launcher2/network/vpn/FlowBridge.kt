package de.mm20.launcher2.network.vpn

import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.ParcelFileDescriptor
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.DNSOpts
import com.celzero.firestack.backend.DNSSummary
import com.celzero.firestack.backend.DomainOpts
import com.celzero.firestack.backend.ServerSummary
import com.celzero.firestack.backend.Tab
import com.celzero.firestack.intra.Bridge
import com.celzero.firestack.intra.FlowSummary
import com.celzero.firestack.intra.Mark
import com.celzero.firestack.intra.PreMark
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.BlocklistController
import de.mm20.launcher2.network.api.ConnectionLogEntry
import de.mm20.launcher2.network.api.DecisionReason
import de.mm20.launcher2.network.api.DnsController
import de.mm20.launcher2.network.api.DnsLogEntry
import de.mm20.launcher2.network.api.DnsQuery
import de.mm20.launcher2.network.api.DnsVerdict
import de.mm20.launcher2.network.api.FirewallController
import de.mm20.launcher2.network.api.FlowDecision
import de.mm20.launcher2.network.api.FlowEnvironment
import de.mm20.launcher2.network.api.FlowInfo
import de.mm20.launcher2.network.api.LogController
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.api.Protocol
import de.mm20.launcher2.network.api.Verdict
import de.mm20.launcher2.network.api.WgRoute
import de.mm20.launcher2.network.api.WireguardController
import de.mm20.launcher2.network.util.IpUtil
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The object the Go engine calls back into (firestack's `Bridge`). Go calls these methods from its
 * own threads for every new connection and every DNS question, and waits for the answer, so
 * everything here is quick and in-memory.
 *
 * Fail-open: no exception may leave a method. A failure while deciding allows the connection, and
 * too many failures in a short time ask the service to shut the VPN down ([onFault]).
 *
 * Adapted from Rethink DNS' `BraveVPNService` / `TunFlowManager` / `TunDnsManager` (Apache-2.0),
 * trimmed to the decisions Telos Network makes.
 */
internal class FlowBridge(
    private val connectivity: ConnectivityManager,
    private val environment: () -> FlowEnvironment,
    private val protectFd: (Int) -> Boolean,
    private val networkFor: (ipv6: Boolean) -> Network?,
    private val firewall: FirewallController,
    private val wireguard: WireguardController,
    private val dns: DnsController,
    private val blocklists: BlocklistController,
    private val logs: LogController,
    private val settings: NetworkSettings,
    private val apps: AppDirectory,
    private val onFault: (String) -> Unit,
) : Bridge {

    private val faultCount = AtomicInteger(0)
    private val faultWindowStart = AtomicLong(0)
    private val ownAppId = apps.ownAppId

    // --- sockets of the engine itself: they must not loop back into the tunnel ---

    override fun protect(who: String?, fd: Long) {
        protectFd(fd.toInt())
    }

    override fun bind4(who: String?, addrPort: String?, fid: Long) = bind(fid, ipv6 = false)

    override fun bind6(who: String?, addrPort: String?, fid: Long) = bind(fid, ipv6 = true)

    private fun bind(fid: Long, ipv6: Boolean) {
        try {
            protectFd(fid.toInt())
            val network = networkFor(ipv6) ?: return
            var pfd: ParcelFileDescriptor? = null
            try {
                pfd = ParcelFileDescriptor.adoptFd(fid.toInt())
                network.bindSocket(pfd.fileDescriptor)
            } finally {
                // the descriptor belongs to the engine, never close it here
                pfd?.detachFd()
            }
        } catch (e: Exception) {
            // the socket stays protected, which is enough to keep it out of the tunnel
        }
    }

    // --- connections ---

    override fun preflow(protocol: Int, uid: Int, src: String?, dst: String?): PreMark {
        val mark = PreMark()
        try {
            val owner = resolveUid(protocol, uid, src, dst)
            mark.setUID(owner.toString())
            mark.setIsUidSelf(owner >= 0 && owner % FlowInfo.PER_USER_RANGE == ownAppId)
        } catch (t: Throwable) {
            fault("preflow", t)
            mark.setUID(uid.toString())
            mark.setIsUidSelf(false)
        }
        return mark
    }

    override fun flow(
        protocol: Int,
        uid: Int,
        src: String?,
        dst: String?,
        realIps: String?,
        d: String?,
        probableDomains: String?,
        blocklists: String?,
        isAlg: Boolean,
    ): Mark {
        val cid = newConnectionId()
        try {
            val (srcIp, srcPort) = IpUtil.splitHostPort(src)
            val (dstIp, dstPort) = IpUtil.splitHostPort(dst)
            val owner = resolveUid(protocol, uid, src, dst)

            // Questions to the fake DNS address of the tunnel are answered by the engine itself
            if (isTunnelDns(dstIp)) {
                // DNS-over-TLS cannot be answered by the tunnel's resolver
                return if (dstPort == DOT_PORT) mark(Backend.Block, cid, owner) else mark(Backend.Base, cid, owner)
            }

            val ips = realIps.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val realDestIp = ips.firstOrNull { !IpUtil.isUnspecified(it) } ?: dstIp
            val domain = d.orEmpty().split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() }
            val lists = blocklists.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val info = FlowInfo(
                flowId = cid,
                uid = owner,
                protocol = Protocol.fromNumber(protocol),
                sourceIp = srcIp,
                sourcePort = srcPort,
                destIp = realDestIp,
                destPort = dstPort,
                domain = domain,
                blocklists = lists,
                dnsBlocked = ips.any { IpUtil.isUnspecified(it) },
                environment = environment(),
            )

            val appId = info.appId
            if (appId == ownAppId) {
                // Telos itself is never filtered; normally it is not even routed through the tunnel
                return mark(Backend.Exit, cid, owner)
            }

            val decision = try {
                firewall.decide(info)
            } catch (t: Throwable) {
                fault("decide", t)
                FlowDecision.allow(DecisionReason.EngineError)
            }

            var proxy = Backend.Exit
            var via: String? = null
            var verdict = decision.verdict
            var reason = decision.reason
            if (decision.verdict == Verdict.Allow) {
                when (val route = safeRoute(info)) {
                    WgRoute.Direct -> {}
                    WgRoute.Block -> {
                        verdict = Verdict.Block
                        reason = DecisionReason.AppRule
                    }
                    is WgRoute.Via -> {
                        proxy = route.proxyId
                        via = route.proxyId
                    }
                }
            }
            if (verdict == Verdict.Block) proxy = Backend.Block

            record(info, verdict, reason, decision.ruleId, via)
            return mark(proxy, cid, owner)
        } catch (t: Throwable) {
            fault("flow", t)
            return mark(Backend.Exit, cid, uid)
        }
    }

    override fun inflow(protocol: Int, recvdUid: Int, src: String?, dst: String?): Mark {
        val cid = newConnectionId()
        try {
            val (srcIp, srcPort) = IpUtil.splitHostPort(src)
            val (dstIp, dstPort) = IpUtil.splitHostPort(dst)
            val owner = resolveUid(protocol, recvdUid, src, dst)
            val info = FlowInfo(
                flowId = cid,
                uid = owner,
                protocol = Protocol.fromNumber(protocol),
                sourceIp = srcIp,
                sourcePort = srcPort,
                destIp = dstIp,
                destPort = dstPort,
                incoming = true,
                environment = environment(),
            )
            val decision = try {
                firewall.decide(info)
            } catch (t: Throwable) {
                fault("decide-in", t)
                FlowDecision.allow(DecisionReason.EngineError)
            }
            record(info, decision.verdict, decision.reason, decision.ruleId, null)
            // Ingress is a placeholder proxy id for allowed incoming connections
            return mark(if (decision.blocked) Backend.Block else Backend.Ingress, cid, owner)
        } catch (t: Throwable) {
            fault("inflow", t)
            return mark(Backend.Ingress, cid, recvdUid)
        }
    }

    override fun flowing(m: Mark?) {
        // nothing to do: the connection was marked and is now being forwarded
    }

    override fun postflow(s: FlowSummary?) {
        try {
            if (s == null || s.getID().isNullOrEmpty()) return
            logs.recordConnectionEnd(s.getID(), s.getRx(), s.getTx(), s.getDuration())
        } catch (t: Throwable) {
            fault("postflow", t)
        }
    }

    private fun safeRoute(info: FlowInfo): WgRoute = try {
        wireguard.routeFor(info)
    } catch (t: Throwable) {
        fault("route", t)
        WgRoute.Direct
    }

    private fun record(info: FlowInfo, verdict: Verdict, reason: DecisionReason, ruleId: Long?, proxyId: String?) {
        if (!settings.current.logConnections) return
        logs.recordConnection(
            ConnectionLogEntry(
                flowId = info.flowId,
                timeMs = info.timestampMs,
                uid = info.uid,
                protocol = info.protocol,
                destIp = info.destIp,
                destPort = info.destPort,
                domain = info.domain,
                verdict = verdict,
                reason = reason,
                ruleId = ruleId,
                proxyId = proxyId,
                network = info.environment.network,
                blocklists = info.blocklists,
            )
        )
    }

    private fun mark(proxyIds: String, cid: String, uid: Int): Mark {
        val m = Mark()
        m.setPIDCSV(proxyIds)
        m.setCID(cid)
        m.setUID(uid.toString())
        return m
    }

    private fun newConnectionId(): String {
        val n = connectionCounter.incrementAndGet()
        return java.lang.Long.toHexString(System.currentTimeMillis()) + "-" + java.lang.Long.toHexString(n)
    }

    /** Uses the uid the engine knows, or asks Android who owns the connection (API 29+). -1 if unknown. */
    private fun resolveUid(protocol: Int, uid: Int, src: String?, dst: String?): Int {
        if (uid != FlowInfo.UNKNOWN_UID) return uid
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uid
        return try {
            val (srcIp, srcPort) = IpUtil.splitHostPort(src)
            val (dstIp, dstPort) = IpUtil.splitHostPort(dst)
            val from = IpUtil.parse(srcIp) ?: return uid
            val to = IpUtil.parse(dstIp) ?: return uid
            connectivity.getConnectionOwnerUid(protocol, InetSocketAddress(from, srcPort), InetSocketAddress(to, dstPort))
        } catch (e: Exception) {
            uid
        }
    }

    private fun isTunnelDns(ip: String): Boolean = ip == TunnelConstants.DNS_V4 || ip.equals(TunnelConstants.DNS_V6, ignoreCase = true)

    // --- DNS ---

    override fun onQuery(origin: String?, uid: String?, fqdn: String?, qtype: Long): DNSOpts {
        val opts = DNSOpts()
        val owner = uid?.toIntOrNull() ?: FlowInfo.UNKNOWN_UID
        val domain = fqdn.orEmpty().trimEnd('.')
        try {
            val verdict = try {
                firewall.decideDns(DnsQuery(owner, domain, qtype.toInt()))
            } catch (t: Throwable) {
                fault("decideDns", t)
                DnsVerdict.Allow
            }
            val transport = if (verdict == DnsVerdict.Block) {
                Backend.BlockAll
            } else {
                Backend.CT + dns.transportFor(owner, domain)
            }
            opts.setUID(uid.orEmpty())
            opts.setIPCSV("")
            // "<transport>:<proxy>": DNS itself goes out directly (Base), not through WireGuard
            opts.setTIDCSV("$transport:${Backend.Base}")
            opts.setTIDSECCSV("")
            opts.setNOBLOCK(verdict == DnsVerdict.AllowSkipBlocklists)
        } catch (t: Throwable) {
            fault("onQuery", t)
            opts.setUID(uid.orEmpty())
            opts.setIPCSV("")
            opts.setTIDCSV("${Backend.System}:${Backend.Base}")
            opts.setTIDSECCSV("")
            opts.setNOBLOCK(false)
        }
        return opts
    }

    override fun onUpstreamAnswer(id: String?, smm: DNSSummary?, rcvdDnsOpts: DNSOpts?, ipcsv: String?): DNSOpts {
        // An empty DNSOpts means "no change": the options of the question (transport, and NOBLOCK for
        // trusted/bypassed domains) stay in force. Returning the received options instead would make
        // the engine start the resolve again (see Rethink's TunDnsManager.onUpstreamAnswer).
        return DNSOpts()
    }

    override fun onPrequery(a: String?, b: String?, c: String?, d: Long): DomainOpts? = null

    override fun onResponse(summary: DNSSummary?) {
        try {
            if (summary == null) return
            val answers = summary.getRData().orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val lists = summary.getBlocklists().orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val transport = summary.getID().orEmpty()
            val blocked = transport == Backend.BlockAll || transport == Backend.Block ||
                (answers.isNotEmpty() && IpUtil.isUnspecified(answers.first())) ||
                (answers.isEmpty() && (lists.isNotEmpty() || summary.getUpstreamBlocks()))
            if (blocked && lists.isNotEmpty()) blocklists.recordBlock(lists)
            if (!settings.current.logDns) return
            val status = summary.getStatus()
            logs.recordDns(
                DnsLogEntry(
                    timeMs = summary.getStart(),
                    uid = summary.getUID().orEmpty().toIntOrNull() ?: FlowInfo.UNKNOWN_UID,
                    domain = summary.getQName().orEmpty().trimEnd('.'),
                    type = summary.getQType().toInt(),
                    responseCode = summary.getRCode().toInt(),
                    answers = answers,
                    server = summary.getServer().orEmpty(),
                    latencyMs = (summary.getLatency() * 1000).toLong(),
                    cached = summary.getCached(),
                    blocked = blocked,
                    blocklists = lists,
                    error = if (status != Backend.Complete) summary.getMsg()?.takeIf { it.isNotBlank() } else null,
                )
            )
        } catch (t: Throwable) {
            fault("onResponse", t)
        }
    }

    // --- events of the engine that need no reaction ---

    override fun onDNSAdded(id: String?) {}

    override fun onDNSRemoved(id: String?) {}

    override fun onDNSStopped() {}

    override fun onProxiesStopped() {}

    override fun onProxyAdded(id: String?, handle: String?) {}

    override fun onProxyRemoved(id: String?, handle: String?) {}

    override fun onProxyStopped(id: String?, handle: String?) {}

    override fun onProxyUpdated(id: String?, handle: String?) {}

    override fun onSvcComplete(summary: ServerSummary?) {}

    override fun svcRoute(sid: String?, pid: String?, network: String?, sipport: String?, dipport: String?): Tab = Tab()

    /** Counts failures; many in a short time mean the engine cannot be trusted any more. */
    private fun fault(where: String, t: Throwable) {
        val now = System.currentTimeMillis()
        if (now - faultWindowStart.get() > FAULT_WINDOW_MS) {
            faultWindowStart.set(now)
            faultCount.set(0)
        }
        if (faultCount.incrementAndGet() > MAX_FAULTS_IN_WINDOW) {
            faultCount.set(0)
            onFault("$where: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private companion object {
        const val DOT_PORT = 853
        const val FAULT_WINDOW_MS = 10_000L
        const val MAX_FAULTS_IN_WINDOW = 50
        val connectionCounter = AtomicLong(0)
    }
}
