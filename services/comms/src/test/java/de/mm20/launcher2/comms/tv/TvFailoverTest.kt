package de.mm20.launcher2.comms.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFailoverTest {
    private fun s(n: Int) = TvStream("https://x.example/$n.m3u8")
    private val streams = listOf(s(1), s(2), s(3))
    private val now = 1_000_000_000L

    @Test fun orderPutsPreferredFirstAndBadLast() {
        val bad = mapOf(s(1).url to now - 1000)
        val o = TvFailover.order(streams, s(3).url, bad, now)
        assertEquals(listOf(s(3), s(2), s(1)).map { it.url }, o.map { it.url })
    }

    @Test fun preferredThatIsBadIsNotFirst() {
        val bad = mapOf(s(3).url to now - 1000)
        assertEquals(listOf(1, 2, 3).map { s(it).url }, TvFailover.order(streams, s(3).url, bad, now).map { it.url })
    }

    @Test fun expiredBadEntriesAreIgnored() {
        val bad = mapOf(s(1).url to now - TvFailover.BAD_TTL_MS - 1)
        assertEquals(streams.map { it.url }, TvFailover.order(streams, null, bad, now).map { it.url })
        assertTrue(TvFailover.prune(bad, now).isEmpty())
    }

    @Test fun duplicatesAreDropped() {
        assertEquals(3, TvFailover.order(streams + s(2), null, emptyMap(), now).size)
    }

    @Test fun sequenceTriesAllThenChecksCatalogThenGivesUp() {
        val tried = HashSet<String>()
        val order = TvFailover.order(streams, null, emptyMap(), now)
        for (i in 1..3) {
            val d = TvFailover.next(order, tried, false)
            assertTrue(d is TvFailover.Decision.TryStream)
            tried.add((d as TvFailover.Decision.TryStream).stream.url)
        }
        assertEquals(TvFailover.Decision.CheckCatalog, TvFailover.next(order, tried, false))
        assertEquals(TvFailover.Decision.GiveUp, TvFailover.next(order, tried, true))
    }

    @Test fun newStreamsAfterCatalogUpdateAreTried() {
        val tried = streams.map { it.url }.toSet()
        val updated = TvFailover.order(streams + s(4), null, emptyMap(), now)
        val d = TvFailover.next(updated, tried, true)
        assertEquals(TvFailover.Decision.TryStream(s(4)), d)
    }

    @Test fun staleness() {
        assertTrue(TvFailover.isStale(0, now))
        assertFalse(TvFailover.isStale(now - 1000, now))
        assertTrue(TvFailover.isStale(now - TvFailover.STALE_AFTER_MS - 1, now))
    }
}
