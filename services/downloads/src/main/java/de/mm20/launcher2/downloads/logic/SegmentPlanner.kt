package de.mm20.launcher2.downloads.logic

import de.mm20.launcher2.downloads.SegmentState

/** Pure logic to split a file into byte ranges, and to split a range again when a connection is idle. */
object SegmentPlanner {
    const val MIN_CONNECTIONS = 1
    const val MAX_CONNECTIONS = 16

    /** No range is made smaller than this when planning */
    const val MIN_SEGMENT = 512L * 1024

    /** A running range is only split when both halves are at least this big */
    const val MIN_SPLIT = 256L * 1024

    fun clampConnections(n: Int) = n.coerceIn(MIN_CONNECTIONS, MAX_CONNECTIONS)

    /** Ranges for a file of [total] bytes with up to [connections] connections. Unknown size: one open range. */
    fun plan(total: Long, connections: Int, minSegment: Long = MIN_SEGMENT): List<SegmentState> {
        if (total <= 0) return listOf(SegmentState(0, 0, -1))
        val wanted = clampConnections(connections)
        val count = minOf(wanted.toLong(), maxOf(1L, total / minSegment)).toInt()
        val base = total / count
        return (0 until count).map { i ->
            val start = i * base
            val end = if (i == count - 1) total - 1 else start + base - 1
            SegmentState(i, start, end)
        }
    }

    /**
     * Where to cut a range that is at [position] (next byte to read) and ends at [end] (inclusive).
     * The new range would be [result, end], the old one then ends at result - 1. Null: too small to split.
     */
    fun splitPoint(position: Long, end: Long, minSplit: Long = MIN_SPLIT): Long? {
        if (end < 0 || position > end) return null
        val remaining = end - position + 1
        if (remaining < 2 * minSplit) return null
        return position + remaining / 2
    }

    fun downloadedTotal(segments: List<SegmentState>): Long =
        segments.sumOf { if (it.end >= 0) minOf(it.downloaded, it.length) else it.downloaded }

    fun isComplete(segments: List<SegmentState>): Boolean = segments.isNotEmpty() && segments.all { it.isComplete }

    /** Ranges are consistent: they cover [0, total) without gaps or overlap */
    fun covers(segments: List<SegmentState>, total: Long): Boolean {
        if (total <= 0) return segments.size == 1 && segments[0].end < 0
        var next = 0L
        for (s in segments.sortedBy { it.start }) {
            if (s.start != next || s.end < s.start) return false
            next = s.end + 1
        }
        return next == total
    }
}
