package de.mm20.launcher2.downloads.engine

import kotlinx.coroutines.delay

/**
 * Limits bytes per second by handing out time slots. One instance is shared by all connections it
 * should limit (the global limit is shared by all tasks, a task limit by its connections).
 */
class RateLimiter(private val nanoTime: () -> Long = System::nanoTime) {
    @Volatile
    var bytesPerSecond: Long = 0

    private var nextFreeNs = 0L

    /** Reserves [bytes] and returns how many nanoseconds the caller has to wait before using them */
    @Synchronized
    fun reserve(bytes: Int): Long {
        val rate = bytesPerSecond
        if (rate <= 0 || bytes <= 0) return 0
        val now = nanoTime()
        val start = maxOf(now, nextFreeNs)
        // do not bank unused time: a long idle period must not allow a burst
        nextFreeNs = start + bytes * 1_000_000_000L / rate
        return start - now
    }

    suspend fun acquire(bytes: Int) {
        val waitNs = reserve(bytes)
        if (waitNs > 1_000_000) delay(waitNs / 1_000_000)
    }
}
