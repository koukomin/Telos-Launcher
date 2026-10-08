package de.mm20.launcher2.downloads.logic

import de.mm20.launcher2.downloads.DownloadState
import de.mm20.launcher2.downloads.DownloadTask

/** What the device is doing right now */
data class Conditions(
    val online: Boolean = true,
    /** Not a metered connection (Wi-Fi, ethernet) */
    val unmetered: Boolean = true,
    val batteryPercent: Int = 100,
    val charging: Boolean = false,
)

data class QueueSettings(
    val maxParallel: Int = 3,
    val wifiOnly: Boolean = false,
    val pauseOnLowBattery: Boolean = false,
    val lowBatteryPercent: Int = 15,
)

enum class BlockReason { Offline, WifiOnly, LowBattery }

/** Which queued tasks may start now. Pure, see QueueRulesTest. */
object QueueRules {

    fun blockReason(c: Conditions, s: QueueSettings): BlockReason? = when {
        !c.online -> BlockReason.Offline
        s.wifiOnly && !c.unmetered -> BlockReason.WifiOnly
        s.pauseOnLowBattery && !c.charging && c.batteryPercent <= s.lowBatteryPercent -> BlockReason.LowBattery
        else -> null
    }

    /**
     * Tasks to start now, in start order: queued ones whose retry time has come, highest priority first,
     * then oldest first, up to the free slots. Nothing starts while the conditions block downloads.
     */
    fun pickNext(
        tasks: List<DownloadTask>,
        now: Long,
        conditions: Conditions,
        settings: QueueSettings,
        canRun: (DownloadTask) -> Boolean = { true },
    ): List<DownloadTask> {
        if (blockReason(conditions, settings) != null) return emptyList()
        // a torrent that only seeds does not take a download slot
        val running = tasks.count { it.state.isActive && it.state != DownloadState.Seeding }
        val free = (settings.maxParallel.coerceAtLeast(1) - running).coerceAtLeast(0)
        if (free == 0) return emptyList()
        return tasks.asSequence()
            .filter { it.state == DownloadState.Queued && it.nextRetryAt <= now && canRun(it) }
            .sortedWith(compareByDescending<DownloadTask> { it.priority }.thenBy { it.createdAt })
            .take(free)
            .toList()
    }

    /** The earliest retry time of queued tasks that wait for it, null if none */
    fun nextWakeUp(tasks: List<DownloadTask>, now: Long): Long? =
        tasks.filter { it.state == DownloadState.Queued && it.nextRetryAt > now }.minOfOrNull { it.nextRetryAt }
}

/** When and whether a failed download is tried again */
object RetryPolicy {
    const val BASE_MS = 5_000L
    const val MAX_MS = 5 * 60_000L

    /** Wait before retry number [attempt] (1 for the first retry): 5 s, 10 s, 20 s ... at most 5 minutes */
    fun backoffMs(attempt: Int, baseMs: Long = BASE_MS, maxMs: Long = MAX_MS): Long {
        if (attempt <= 0) return 0
        val shift = (attempt - 1).coerceAtMost(30)
        val v = baseMs * (1L shl shift)
        return if (v < 0 || v > maxMs) maxMs else v
    }

    fun shouldRetry(retryable: Boolean, retriesSoFar: Int, maxRetries: Int): Boolean =
        retryable && retriesSoFar < maxRetries

    /** HTTP status codes worth trying again */
    fun isRetryableStatus(code: Int) = code == 408 || code == 425 || code == 429 || code in 500..599
}
