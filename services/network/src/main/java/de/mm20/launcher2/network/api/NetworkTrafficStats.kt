package de.mm20.launcher2.network.api

/** Byte totals of retained flows grouped by their opening time, not an OS traffic-meter replacement. */
data class NetworkTrafficTotals(
    val received: Long = 0,
    val sent: Long = 0,
    val metered: Long = 0,
    val unmetered: Long = 0,
    val unknownMetering: Long = 0,
    val connections: Int = 0,
    val blocked: Int = 0,
    val apps: List<AppTrafficTotals> = emptyList(),
) {
    val bytes: Long get() = saturatingBytes(received, sent)
}
data class AppTrafficTotals(val appId: Int, val received: Long, val sent: Long, val connections: Int) {
    val bytes: Long get() = saturatingBytes(received, sent)
}
private fun saturatingBytes(a: Long, b: Long): Long = if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b

object NetworkTrafficStats {
    fun aggregate(entries: List<ConnectionLogEntry>, sinceMs: Long, untilMs: Long): NetworkTrafficTotals {
        var received = 0L; var sent = 0L; var metered = 0L; var unmetered = 0L; var unknown = 0L
        var connections = 0; var blocked = 0
        val apps = HashMap<Int, AppTrafficTotals>()
        for (entry in entries) {
            if (entry.timeMs !in sinceMs..untilMs) continue
            val rx = entry.bytesReceived.coerceAtLeast(0)
            val tx = entry.bytesSent.coerceAtLeast(0)
            val bytes = saturatingBytes(rx, tx)
            received = saturatingBytes(received, rx); sent = saturatingBytes(sent, tx)
            when (entry.metered) {
                true -> metered = saturatingBytes(metered, bytes)
                false -> unmetered = saturatingBytes(unmetered, bytes)
                null -> unknown = saturatingBytes(unknown, bytes)
            }
            connections++
            if (entry.verdict == Verdict.Block) blocked++
            val appId = if (entry.uid < 0) -1 else entry.uid % 100000
            val previous = apps[appId] ?: AppTrafficTotals(appId, 0, 0, 0)
            apps[appId] = previous.copy(received = saturatingBytes(previous.received, rx), sent = saturatingBytes(previous.sent, tx), connections = previous.connections + 1)
        }
        return NetworkTrafficTotals(received, sent, metered, unmetered, unknown, connections, blocked, apps.values.sortedByDescending { it.bytes })
    }
}
