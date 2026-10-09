package de.mm20.launcher2.network.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** A connection in the log. */
@Serializable
data class ConnectionLogEntry(
    /** Assigned by the store when recorded; 0 when still to be recorded. */
    val id: Long = 0,
    /** The flow id; unique. */
    val flowId: String,
    val timeMs: Long,
    val uid: Int,
    val protocol: Protocol,
    val destIp: String,
    val destPort: Int,
    val domain: String? = null,
    val verdict: Verdict,
    val reason: DecisionReason,
    val ruleId: Long? = null,
    /** Proxy id when the connection went through WireGuard, otherwise null. */
    val proxyId: String? = null,
    val network: ConnectionType = ConnectionType.Other,
    val blocklists: List<String> = emptyList(),
    /** Filled in when the connection ended. */
    val bytesReceived: Long = 0,
    val bytesSent: Long = 0,
    val durationMs: Long = 0,
)

/** A DNS question and its answer in the log. */
@Serializable
data class DnsLogEntry(
    val id: Long = 0,
    val timeMs: Long,
    val uid: Int,
    val domain: String,
    /** Numeric record type (1 = A, 28 = AAAA...). */
    val type: Int,
    /** DNS response code, 0 = NOERROR, 3 = NXDOMAIN. */
    val responseCode: Int = 0,
    /** Addresses or data of the answer. */
    val answers: List<String> = emptyList(),
    /** Display name of the server that answered. */
    val server: String = "",
    val latencyMs: Long = 0,
    val cached: Boolean = false,
    val blocked: Boolean = false,
    val blocklists: List<String> = emptyList(),
    /** Error text when the query failed. */
    val error: String? = null,
)

/** Filter for log queries. All set conditions must match. */
data class LogFilter(
    /** Case-insensitive text found in domain, address or app name. */
    val query: String? = null,
    /** Only entries of this app id. */
    val appId: Int? = null,
    /** `true` = only blocked, `false` = only allowed, `null` = both. */
    val blocked: Boolean? = null,
    val sinceMs: Long? = null,
    val limit: Int = 500,
)

/** Totals for the dashboard. */
@Serializable
data class LogStats(
    val connectionsAllowed: Long = 0,
    val connectionsBlocked: Long = 0,
    val dnsAllowed: Long = 0,
    val dnsBlocked: Long = 0,
)

/** Per app totals of the logs. */
data class AppLogSummary(
    val appId: Int,
    val connections: Int,
    val connectionsBlocked: Int,
    val dnsQueries: Int,
    val dnsBlocked: Int,
    val bytes: Long,
    val lastMs: Long,
)

/**
 * Bounded log store for connections and DNS. The newest entries come first. Limits come from
 * [NetworkSettings] (`logMaxEntries`, `logRetentionDays`); the `log*` switches there tell the
 * engine whether to call the record methods at all.
 */
interface LogController {
    /** Newest connections first, at most 500 for UI use. */
    val connections: StateFlow<List<ConnectionLogEntry>>

    /** Newest DNS entries first, at most 500 for UI use. */
    val dns: StateFlow<List<DnsLogEntry>>

    /** Totals per app (by app id) over the kept entries, most active first. */
    val appSummaries: StateFlow<List<AppLogSummary>>

    /** The log as CSV text (header line included) for [filter]; `dns` selects the DNS log. */
    suspend fun exportCsv(dns: Boolean, filter: LogFilter = LogFilter(limit = Int.MAX_VALUE)): String

    /** Removes the entries of one app from both logs. */
    suspend fun clearApp(appId: Int)

    /** Counters since the logs were cleared. */
    val stats: StateFlow<LogStats>

    /** A filtered, live view of the connection log. */
    fun connections(filter: LogFilter): Flow<List<ConnectionLogEntry>>

    /** A filtered, live view of the DNS log. */
    fun dns(filter: LogFilter): Flow<List<DnsLogEntry>>

    /** Records a connection. Called on engine threads: must return quickly (queue the write). */
    fun recordConnection(entry: ConnectionLogEntry)

    /** Adds the final numbers to a recorded connection. Ignored when the entry is gone. */
    fun recordConnectionEnd(flowId: String, bytesReceived: Long, bytesSent: Long, durationMs: Long)

    /** Records a DNS question. Called on engine threads: must return quickly. */
    fun recordDns(entry: DnsLogEntry)

    suspend fun clearConnections()

    suspend fun clearDns()

    /** Clears both logs and the counters. */
    suspend fun clearAll()
}
