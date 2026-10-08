package de.mm20.launcher2.downloads

import de.mm20.launcher2.downloads.logic.SegmentPlanner
import org.junit.Assert.*
import org.junit.Test

class SegmentPlannerTest {
    @Test fun smallFileGetsOneSegment() {
        val s = SegmentPlanner.plan(100_000, 8)
        assertEquals(1, s.size)
        assertEquals(0L, s[0].start); assertEquals(99_999L, s[0].end)
    }

    @Test fun plansCoverTheFileWithoutGaps() {
        for (total in listOf(1L, 1_000_003L, 10_000_000L, 123_456_789L)) for (c in 1..16) {
            val s = SegmentPlanner.plan(total, c)
            assertTrue("total=$total c=$c", SegmentPlanner.covers(s, total))
            assertTrue(s.size <= c)
        }
    }

    @Test fun connectionsAreClamped() {
        assertEquals(16, SegmentPlanner.plan(1_000_000_000, 99).size)
        assertEquals(1, SegmentPlanner.plan(1_000_000_000, 0).size)
        assertEquals(1, SegmentPlanner.plan(1_000_000_000, -5).size)
    }

    @Test fun segmentsAreNotSmallerThanMinimum() {
        val s = SegmentPlanner.plan(2 * 1024 * 1024L, 16, minSegment = 512 * 1024)
        assertEquals(4, s.size)
    }

    @Test fun unknownSizeIsOneOpenSegment() {
        val s = SegmentPlanner.plan(-1, 8)
        assertEquals(1, s.size); assertEquals(-1L, s[0].end)
        assertTrue(SegmentPlanner.covers(s, -1))
    }

    @Test fun splitPoint() {
        assertNull(SegmentPlanner.splitPoint(0, 100_000))
        assertNull(SegmentPlanner.splitPoint(500, -1))
        assertNull(SegmentPlanner.splitPoint(1000, 999))
        val cut = SegmentPlanner.splitPoint(0, 1_999_999)!!
        assertEquals(1_000_000L, cut)
        val cut2 = SegmentPlanner.splitPoint(1_000_000, 1_999_999)!!
        assertEquals(1_500_000L, cut2)
    }

    @Test fun downloadedTotalIgnoresOverlap() {
        val segs = listOf(SegmentState(0, 0, 99, 150), SegmentState(1, 100, 199, 50))
        assertEquals(150L, SegmentPlanner.downloadedTotal(segs))
        assertFalse(SegmentPlanner.isComplete(segs))
        assertTrue(SegmentPlanner.isComplete(listOf(SegmentState(0, 0, 99, 100))))
    }

    @Test fun coversDetectsGaps() {
        assertFalse(SegmentPlanner.covers(listOf(SegmentState(0, 0, 49), SegmentState(1, 60, 99)), 100))
        assertFalse(SegmentPlanner.covers(listOf(SegmentState(0, 0, 49)), 100))
    }
}
