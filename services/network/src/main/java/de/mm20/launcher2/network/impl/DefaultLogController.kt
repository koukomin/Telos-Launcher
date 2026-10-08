package de.mm20.launcher2.network.impl

import android.content.Context
import android.util.AtomicFile
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.AppLogSummary
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Ring buffers bounded by the settings (`logMaxEntries`, `logRetentionDays`), saved as JSON files in
 * `filesDir/network/log/` every [SAVE_INTERVAL_MS] while something changed and read back at start,
 * so the logs survive a restart of the app (the last few seconds before a crash can be lost). The
 * UI flows are republished at most about three times a second, however fast connections come in.
 */
internal class DefaultLogController(
    context: Context,
    private val settings: NetworkSettings,
    private val apps: AppDirectory,
) : LogController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val dir = File(File(context.filesDir, "network"), "log")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private var diskDirty = false

    // newest first
    private val connectionBuffer = ArrayDeque<ConnectionLogEntry>()
    private val dnsBuffer = ArrayDeque<DnsLogEntry>()
    private var nextId = 1L
    private var dirty = false
    private var statsValue = LogStats()

    private val _summaries = MutableStateFlow<List<AppLogSummary>>(emptyList())
    override val appSummaries: StateFlow<List<AppLogSummary>> = _summaries

    private val _connections = MutableStateFlow<List<ConnectionLogEntry>>(emptyList())
    override val connections: StateFlow<List<ConnectionLogEntry>> = _connections

    private val _dns = MutableStateFlow<List<DnsLogEntry>>(emptyList())
    override val dns: StateFlow<List<DnsLogEntry>> = _dns

    private val _stats = MutableStateFlow(LogStats())
    override val stats: StateFlow<LogStats> = _stats

    /** Incremented on each publish; the filtered flows re-query when it changes. */
    private val version = MutableStateFlow(0L)

    init {
        load()
        dirty = true
        scope.launch {
            var sinceSave = 0L
            while (true) {
                delay(PUBLISH_INTERVAL_MS)
                publishIfDirty()
                sinceSave += PUBLISH_INTERVAL_MS
                if (sinceSave >= SAVE_INTERVAL_MS) {
                    sinceSave = 0
                    save()
                }
            }
        }
    }

    private fun <T> read(name: String, serializer: kotlinx.serialization.KSerializer<T>): T? = try {
        File(dir, name).takeIf { it.exists() }?.let { json.decodeFromString(serializer, it.readText()) }
    } catch (e: Exception) {
        null
    }

    private fun load() {
        synchronized(lock) {
            read("connections.json", ListSerializer(ConnectionLogEntry.serializer()))?.let { connectionBuffer.addAll(it) }
            read("dns.json", ListSerializer(DnsLogEntry.serializer()))?.let { dnsBuffer.addAll(it) }
            read("stats.json", LogStats.serializer())?.let { statsValue = it }
            nextId = (connectionBuffer.maxOfOrNull { it.id } ?: 0L).coerceAtLeast(dnsBuffer.maxOfOrNull { it.id } ?: 0L) + 1
        }
    }

    private fun <T> write(name: String, serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        try {
            dir.mkdirs()
            val atomic = AtomicFile(File(dir, name))
            val out = atomic.startWrite()
            try {
                out.write(json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8))
                atomic.finishWrite(out)
            } catch (e: Exception) {
                atomic.failWrite(out)
            }
        } catch (e: Exception) {
            // disk full: keep the logs in memory
        }
    }

    private fun save() {
        val conns: List<ConnectionLogEntry>
        val dnsList: List<DnsLogEntry>
        val stats: LogStats
        synchronized(lock) {
            if (!diskDirty) return
            diskDirty = false
            conns = connectionBuffer.toList()
            dnsList = dnsBuffer.toList()
            stats = statsValue
        }
        write("connections.json", ListSerializer(ConnectionLogEntry.serializer()), conns)
        write("dns.json", ListSerializer(DnsLogEntry.serializer()), dnsList)
        write("stats.json", LogStats.serializer(), stats)
    }

    private fun summarize(conns: List<ConnectionLogEntry>, dnsList: List<DnsLogEntry>): List<AppLogSummary> {
        class Acc { var c = 0; var cb = 0; var d = 0; var db = 0; var bytes = 0L; var last = 0L }
        val map = HashMap<Int, Acc>()
        fun key(uid: Int) = if (uid < 0) -1 else uid % PER_USER
        for (e in conns) {
            val a = map.getOrPut(key(e.uid)) { Acc() }
            a.c++
            if (e.verdict == Verdict.Block) a.cb++
            a.bytes += e.bytesReceived + e.bytesSent
            if (e.timeMs > a.last) a.last = e.timeMs
        }
        for (e in dnsList) {
            val a = map.getOrPut(key(e.uid)) { Acc() }
            a.d++
            if (e.blocked) a.db++
            if (e.timeMs > a.last) a.last = e.timeMs
        }
        return map.map { (id, a) -> AppLogSummary(id, a.c, a.cb, a.d, a.db, a.bytes, a.last) }
            .sortedByDescending { it.connections + it.dnsQueries }
    }

    override suspend fun exportCsv(dns: Boolean, filter: LogFilter): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()
        fun csv(v: String?): String {
            val t = v.orEmpty()
            return if (t.any { it == ',' || it == '"' || it == '\n' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
        }
        if (dns) {
            sb.append("time,app,domain,type,rcode,answers,server,blocked,blocklists,error\n")
            val rows = synchronized(lock) { dnsBuffer.toList() }
            rows.filter { matches(filter, it.timeMs, it.uid, it.blocked, it.domain, it.answers.joinToString(",")) }
                .take(filter.limit).forEach {
                    sb.append(listOf(
                        java.time.Instant.ofEpochMilli(it.timeMs).toString(), apps.labelFor(it.uid), it.domain, it.type.toString(),
                        it.responseCode.toString(), it.answers.joinToString(" "), it.server, it.blocked.toString(),
                        it.blocklists.joinToString(" "), it.error ?: "",
                    ).joinToString(",") { v -> csv(v) }).append('\n')
                }
        } else {
            sb.append("time,app,protocol,destination,port,domain,verdict,reason,network,blocklists,bytes_in,bytes_out\n")
            val rows = synchronized(lock) { connectionBuffer.toList() }
            rows.filter { matches(filter, it.timeMs, it.uid, it.verdict == Verdict.Block, it.domain, it.destIp) }
                .take(filter.limit).forEach {
                    sb.append(listOf(
                        java.time.Instant.ofEpochMilli(it.timeMs).toString(), apps.labelFor(it.uid), it.protocol.name, it.destIp,
                        it.destPort.toString(), it.domain ?: "", it.verdict.name, it.reason.name, it.network.name,
                        it.blocklists.joinToString(" "), it.bytesReceived.toString(), it.bytesSent.toString(),
                    ).joinToString(",") { v -> csv(v) }).append('\n')
                }
        }
        sb.toString()
    }

    override suspend fun clearApp(appId: Int) {
        synchronized(lock) {
            connectionBuffer.removeAll { it.uid >= 0 && it.uid % PER_USER == appId }
            dnsBuffer.removeAll { it.uid >= 0 && it.uid % PER_USER == appId }
            dirty = true
            diskDirty = true
        }
        publishIfDirty()
    }

    private fun publishIfDirty() {
        val conns: List<ConnectionLogEntry>
        val dnsList: List<DnsLogEntry>
        val stats: LogStats
        val allConns: List<ConnectionLogEntry>
        val allDns: List<DnsLogEntry>
        synchronized(lock) {
            trim()
            if (!dirty) return
            dirty = false
            allConns = connectionBuffer.toList()
            allDns = dnsBuffer.toList()
            conns = allConns.take(UI_LIMIT)
            dnsList = allDns.take(UI_LIMIT)
            stats = statsValue
            diskDirty = true
        }
        _summaries.value = summarize(allConns, allDns)
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
        const val SAVE_INTERVAL_MS = 20_000L
        const val UI_LIMIT = 500
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val PER_USER = 100000
    }
}
