package de.mm20.launcher2.network.impl

import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.ConnectionLogEntry
import de.mm20.launcher2.network.api.DnsLogEntry
import de.mm20.launcher2.network.api.LogController
import de.mm20.launcher2.network.api.LogFilter
import de.mm20.launcher2.network.api.LogStats
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.api.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * In-memory ring buffers, bounded by the settings. Nothing is written to disk, so the logs are gone
 * after the process dies (a persistent store can replace this class). The UI flows are republished
 * at most about three times a second, however fast connections come in.
 */
internal class DefaultLogController(
    private val settings: NetworkSettings,
    private val apps: AppDirectory,
) : LogController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    // newest first
    private val connectionBuffer = ArrayDeque<ConnectionLogEntry>()
    private val dnsBuffer = ArrayDeque<DnsLogEntry>()
    private var nextId = 1L
    private var dirty = false
    private var statsValue = LogStats()

    private val _connections = MutableStateFlow<List<ConnectionLogEntry>>(emptyList())
    override val connections: StateFlow<List<ConnectionLogEntry>> = _connections

    private val _dns = MutableStateFlow<List<DnsLogEntry>>(emptyList())
    override val dns: StateFlow<List<DnsLogEntry>> = _dns

    private val _stats = MutableStateFlow(LogStats())
    override val stats: StateFlow<LogStats> = _stats

    /** Incremented on each publish; the filtered flows re-query when it changes. */
    private val version = MutableStateFlow(0L)

    init {
        scope.launch {
            while (true) {
                delay(PUBLISH_INTERVAL_MS)
                publishIfDirty()
            }
        }
    }

    private fun publishIfDirty() {
        val conns: List<ConnectionLogEntry>
        val dnsList: List<DnsLogEntry>
        val stats: LogStats
        synchronized(lock) {
            trim()
            if (!dirty) return
            dirty = false
            conns = connectionBuffer.take(UI_LIMIT)
            dnsList = dnsBuffer.take(UI_LIMIT)
            stats = statsValue
        }
        _connections.value = conns
        _dns.value = dnsList
        _stats.value = stats
        version.value = version.value + 1
    }

    private fun trim() {
        val values = settings.current
        val max = values.logMaxEntries
        while (connectionBuffer.size > max) connectionBuffer.removeLast()
        while (dnsBuffer.size > max) dnsBuffer.removeLast()
        if (values.logRetentionDays > 0) {
            val oldest = System.currentTimeMillis() - values.logRetentionDays * DAY_MS
            while (connectionBuffer.isNotEmpty() && connectionBuffer.last().timeMs < oldest) connectionBuffer.removeLast()
            while (dnsBuffer.isNotEmpty() && dnsBuffer.last().timeMs < oldest) dnsBuffer.removeLast()
        }
    }

    override fun recordConnection(entry: ConnectionLogEntry) {
        synchronized(lock) {
            connectionBuffer.addFirst(entry.copy(id = nextId++))
            statsValue = if (entry.verdict == Verdict.Block) {
                statsValue.copy(connectionsBlocked = statsValue.connectionsBlocked + 1)
            } else {
                statsValue.copy(connectionsAllowed = statsValue.connectionsAllowed + 1)
            }
            dirty = true
        }
    }

    override fun recordConnectionEnd(flowId: String, bytesReceived: Long, bytesSent: Long, durationMs: Long) {
        synchronized(lock) {
            val index = connectionBuffer.indexOfFirst { it.flowId == flowId }
            if (index < 0) return
            connectionBuffer[index] = connectionBuffer[index].copy(
                bytesReceived = bytesReceived,
                bytesSent = bytesSent,
                durationMs = durationMs,
            )
            dirty = true
        }
    }

    override fun recordDns(entry: DnsLogEntry) {
        synchronized(lock) {
            dnsBuffer.addFirst(entry.copy(id = nextId++))
            statsValue = if (entry.blocked) {
                statsValue.copy(dnsBlocked = statsValue.dnsBlocked + 1)
            } else {
                statsValue.copy(dnsAllowed = statsValue.dnsAllowed + 1)
            }
            dirty = true
        }
    }

    override fun connections(filter: LogFilter): Flow<List<ConnectionLogEntry>> =
        version.map {
            val snapshot = synchronized(lock) { connectionBuffer.toList() }
            snapshot.asSequence().filter { matches(filter, it.timeMs, it.uid, it.verdict == Verdict.Block, it.domain, it.destIp) }
                .take(filter.limit).toList()
        }.flowOn(Dispatchers.Default)

    override fun dns(filter: LogFilter): Flow<List<DnsLogEntry>> =
        version.map {
            val snapshot = synchronized(lock) { dnsBuffer.toList() }
            snapshot.asSequence().filter { matches(filter, it.timeMs, it.uid, it.blocked, it.domain, it.answers.joinToString(",")) }
                .take(filter.limit).toList()
        }.flowOn(Dispatchers.Default)

    private fun matches(filter: LogFilter, timeMs: Long, uid: Int, blocked: Boolean, domain: String?, address: String): Boolean {
        if (filter.sinceMs != null && timeMs < filter.sinceMs) return false
        if (filter.blocked != null && filter.blocked != blocked) return false
        if (filter.appId != null && (uid < 0 || uid % PER_USER != filter.appId)) return false
        val query = filter.query?.trim().orEmpty()
        if (query.isNotEmpty()) {
            val q = query.lowercase()
            val hit = (domain?.lowercase()?.contains(q) == true) ||
                address.lowercase().contains(q) ||
                apps.labelFor(uid).lowercase().contains(q)
            if (!hit) return false
        }
        return true
    }

    override suspend fun clearConnections() {
        synchronized(lock) {
            connectionBuffer.clear()
            dirty = true
        }
        publishIfDirty()
    }

    override suspend fun clearDns() {
        synchronized(lock) {
            dnsBuffer.clear()
            dirty = true
        }
        publishIfDirty()
    }

    override suspend fun clearAll() {
        synchronized(lock) {
            connectionBuffer.clear()
            dnsBuffer.clear()
            statsValue = LogStats()
            dirty = true
        }
        publishIfDirty()
    }

    private companion object {
        const val PUBLISH_INTERVAL_MS = 350L
        const val UI_LIMIT = 500
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val PER_USER = 100000
    }
}
