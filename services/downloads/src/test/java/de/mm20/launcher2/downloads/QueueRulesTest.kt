package de.mm20.launcher2.downloads

import de.mm20.launcher2.downloads.logic.*
import org.junit.Assert.*
import org.junit.Test

class QueueRulesTest {
    private fun task(id: String, state: DownloadState = DownloadState.Queued, created: Long = 0, priority: Int = 0, retryAt: Long = 0) =
        DownloadTask(id = id, url = "https://x/$id", state = state, createdAt = created, priority = priority, nextRetryAt = retryAt)

    private val ok = Conditions()
    private val s = QueueSettings(maxParallel = 2)

    @Test fun startsOldestFirstUpToFreeSlots() {
        val tasks = listOf(task("c", created = 3), task("a", created = 1), task("b", created = 2))
        assertEquals(listOf("a", "b"), QueueRules.pickNext(tasks, 100, ok, s).map { it.id })
    }

    @Test fun runningTasksTakeSlots() {
        val tasks = listOf(task("r", DownloadState.Downloading), task("a", created = 1), task("b", created = 2))
        assertEquals(listOf("a"), QueueRules.pickNext(tasks, 100, ok, s).map { it.id })
        val full = listOf(task("r1", DownloadState.Downloading), task("r2", DownloadState.Connecting), task("a"))
        assertTrue(QueueRules.pickNext(full, 100, ok, s).isEmpty())
    }

    @Test fun priorityBeatsAge() {
        val tasks = listOf(task("old", created = 1), task("urgent", created = 5, priority = 10))
        assertEquals("urgent", QueueRules.pickNext(tasks, 0, ok, s.copy(maxParallel = 1)).single().id)
    }

    @Test fun pausedCompletedFailedAreNotStarted() {
        val tasks = listOf(task("p", DownloadState.Paused), task("c", DownloadState.Completed), task("f", DownloadState.Failed))
        assertTrue(QueueRules.pickNext(tasks, 0, ok, s).isEmpty())
    }

    @Test fun waitsForRetryTime() {
        val tasks = listOf(task("later", retryAt = 500), task("now", retryAt = 100))
        assertEquals(listOf("now"), QueueRules.pickNext(tasks, 200, ok, s).map { it.id })
        assertEquals(500L, QueueRules.nextWakeUp(tasks, 200))
        assertNull(QueueRules.nextWakeUp(tasks, 600))
    }

    @Test fun offlineBlocks() {
        assertEquals(BlockReason.Offline, QueueRules.blockReason(Conditions(online = false), s))
        assertTrue(QueueRules.pickNext(listOf(task("a")), 0, Conditions(online = false), s).isEmpty())
    }

    @Test fun wifiOnlyBlocksMobile() {
        val c = Conditions(unmetered = false)
        assertNull(QueueRules.blockReason(c, s))
        assertEquals(BlockReason.WifiOnly, QueueRules.blockReason(c, s.copy(wifiOnly = true)))
        assertNull(QueueRules.blockReason(Conditions(unmetered = true), s.copy(wifiOnly = true)))
    }

    @Test fun lowBattery() {
        val low = Conditions(batteryPercent = 10)
        val rule = s.copy(pauseOnLowBattery = true, lowBatteryPercent = 15)
        assertEquals(BlockReason.LowBattery, QueueRules.blockReason(low, rule))
        assertNull(QueueRules.blockReason(low.copy(charging = true), rule))
        assertNull(QueueRules.blockReason(Conditions(batteryPercent = 50), rule))
        assertNull(QueueRules.blockReason(low, s))
    }

    @Test fun canRunFilter() {
        val tasks = listOf(task("a"), task("b"))
        assertEquals(listOf("b"), QueueRules.pickNext(tasks, 0, ok, s) { it.id == "b" }.map { it.id })
    }

    @Test fun backoffDoublesAndIsCapped() {
        assertEquals(0L, RetryPolicy.backoffMs(0))
        assertEquals(5_000L, RetryPolicy.backoffMs(1))
        assertEquals(10_000L, RetryPolicy.backoffMs(2))
        assertEquals(20_000L, RetryPolicy.backoffMs(3))
        assertEquals(RetryPolicy.MAX_MS, RetryPolicy.backoffMs(50))
        assertEquals(RetryPolicy.MAX_MS, RetryPolicy.backoffMs(Int.MAX_VALUE))
    }

    @Test fun retryDecision() {
        assertTrue(RetryPolicy.shouldRetry(true, 0, 5))
        assertFalse(RetryPolicy.shouldRetry(true, 5, 5))
        assertFalse(RetryPolicy.shouldRetry(false, 0, 5))
        assertFalse(RetryPolicy.shouldRetry(true, 0, 0))
        assertTrue(RetryPolicy.isRetryableStatus(503)); assertTrue(RetryPolicy.isRetryableStatus(429))
        assertFalse(RetryPolicy.isRetryableStatus(404)); assertFalse(RetryPolicy.isRetryableStatus(403))
    }
}
