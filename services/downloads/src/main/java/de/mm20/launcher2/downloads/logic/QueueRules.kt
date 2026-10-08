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

/** Day of the week (1 = Monday ... 7 = Sunday) and minute of the day, local time */
data class ClockTime(val dayOfWeek: Int, val minuteOfDay: Int)

/** Downloads may only run inside this window. [days] has bit 0 for Monday ... bit 6 for Sunday; a window that ends before it starts runs over midnight. */
data class ScheduleWindow(
    val enabled: Boolean = false,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
    val days: Int = 0b1111111,
)

object Schedule {
    private fun dayOn(w: ScheduleWindow, dow: Int) = (w.days shr ((dow - 1).mod(7))) and 1 == 1

    /** Whether downloads may run at [t]. For a window over midnight the days are the days it starts on. */
    fun isOpen(w: ScheduleWindow, t: ClockTime): Boolean {
        if (!w.enabled) return true
        val m = t.minuteOfDay
        val prev = if (t.dayOfWeek == 1) 7 else t.dayOfWeek - 1
        return when {
            w.startMinute == w.endMinute -> dayOn(w, t.dayOfWeek)
            w.startMinute < w.endMinute -> dayOn(w, t.dayOfWeek) && m >= w.startMinute && m < w.endMinute
            else -> (m >= w.startMinute && dayOn(w, t.dayOfWeek)) || (m < w.endMinute && dayOn(w, prev))
        }
    }

    /** Minutes until the window opens or closes the next time, null when it never changes (switched off, or no day chosen) */
    fun minutesToChange(w: ScheduleWindow, t: ClockTime): Int? {
        if (!w.enabled) return null
        val now = isOpen(w, t)
        var dow = t.dayOfWeek
        var m = t.minuteOfDay
        for (k in 1..7 * 1440) {
            m++
            if (m == 1440) { m = 0; dow = if (dow == 7) 1 else dow + 1 }
            if (isOpen(w, ClockTime(dow, m)) != now) return k
        }
        return null
    }
}

data class QueueSettings(
    val maxParallel: Int = 3,
    val wifiOnly: Boolean = false,
    val pauseOnLowBattery: Boolean = false,
    val lowBatteryPercent: Int = 15,
    val schedule: ScheduleWindow = ScheduleWindow(),
)

enum class BlockReason { Offline, WifiOnly, LowBattery, Schedule }

/** Which queued tasks may start now. Pure, see QueueRulesTest. */
object QueueRules {

    fun blockReason(c: Conditions, s: QueueSettings, clock: ClockTime? = null): BlockReason? = when {
        !c.online -> BlockReason.Offline
        clock != null && !Schedule.isOpen(s.schedule, clock) -> BlockReason.Schedule
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
        clock: ClockTime? = null,
        canRun: (DownloadTask) -> Boolean = { true },
    ): List<DownloadTask> {
        if (blockReason(conditions, settings, clock) != null) return emptyList()
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
