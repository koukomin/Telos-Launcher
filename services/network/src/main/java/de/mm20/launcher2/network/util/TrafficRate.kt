package de.mm20.launcher2.network.util

/** Unknown/reset counters are not a zero-speed measurement. Uses monotonic elapsed time. */
object TrafficRate {
    fun bytesPerSecond(previous: Long, current: Long, elapsedMs: Long): Long? {
        if (previous < 0 || current < previous || elapsedMs <= 0) return null
        return ((current - previous).toDouble() * 1000 / elapsedMs).toLong()
    }
}
