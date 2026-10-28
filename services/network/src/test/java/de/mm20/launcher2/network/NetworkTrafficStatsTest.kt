package de.mm20.launcher2.network

import de.mm20.launcher2.network.api.*
import org.junit.Assert.*
import org.junit.Test

class NetworkTrafficStatsTest {
    private fun flow(id: String, at: Long, metered: Boolean?, rx: Long, tx: Long, uid: Int = 10123) = ConnectionLogEntry(
        flowId = id, timeMs = at, uid = uid, protocol = Protocol.Tcp, destIp = "1.1.1.1", destPort = 443,
        verdict = Verdict.Allow, reason = DecisionReason.Default, bytesReceived = rx, bytesSent = tx, metered = metered)
    @Test fun filtersWindowAndKeepsUnknownOldMeteringSeparate() {
        val result = NetworkTrafficStats.aggregate(listOf(flow("old", 0, true, 999, 999), flow("wifi", 10, false, 10, 2), flow("metered", 11, true, 20, 3), flow("legacy", 12, null, 30, 4), flow("future", 100, false, 999, 999)), 10, 20)
        assertEquals(69L, result.bytes)
        assertEquals(12L, result.unmetered)
        assertEquals(23L, result.metered)
        assertEquals(34L, result.unknownMetering)
        assertEquals(3, result.connections)
        assertEquals(69L, result.apps.single().bytes)
    }
    @Test fun invalidCountersAreClampedAndTotalsCannotOverflow() {
        val result = NetworkTrafficStats.aggregate(listOf(flow("a", 1, true, Long.MAX_VALUE, 1), flow("b", 2, false, -3, -4)), 0, 10)
        assertEquals(Long.MAX_VALUE, result.bytes)
        assertEquals(0L, result.unmetered)
    }
}
