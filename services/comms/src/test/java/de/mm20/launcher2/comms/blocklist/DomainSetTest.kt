package de.mm20.launcher2.comms.blocklist

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainSetTest {
    private val set = DomainSet.of(listOf("ads.example.com", "tracker.net"))

    @Test fun matchesExactAndSubdomains() {
        assertTrue(set.matches("ads.example.com"))
        assertTrue(set.matches("a.b.ADS.example.com."))
        assertTrue(set.matches("x.tracker.net"))
    }

    @Test fun doesNotMatchParentsOrSiblings() {
        assertFalse(set.matches("example.com"))
        assertFalse(set.matches("notads.example.com"))
        assertFalse(set.matches("com"))
        assertFalse(DomainSet.EMPTY.matches("a.b.c"))
    }

    @Test fun hashParsingEqualsStringParsing() {
        val text = "0.0.0.0 a.example.com\n||b.example.org^\nc.example.net\na.example.com\n"
        val viaHashes = DomainSet.fromHashes(BlockListParser.parseDomainHashes(text.reader().buffered()))
        val viaStrings = DomainSet.of(BlockListParser.parseDomains(text.reader().buffered()))
        assertArrayEquals(viaStrings.toArray(), viaHashes.toArray())
        assertTrue(viaHashes.matches("www.b.example.org"))
    }
}
