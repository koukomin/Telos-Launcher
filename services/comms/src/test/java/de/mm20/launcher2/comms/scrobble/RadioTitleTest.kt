package de.mm20.launcher2.comms.scrobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioTitleTest {
    @Test fun splitsArtistAndTitle() {
        assertEquals("Queen" to "Radio Ga Ga", RadioTitle.parse("Queen - Radio Ga Ga", null, "Rock FM"))
    }

    @Test fun usesArtistFieldWhenPresent() {
        assertEquals("Queen" to "Radio Ga Ga", RadioTitle.parse("Radio Ga Ga", "Queen", "Rock FM"))
    }

    @Test fun ignoresJinglesAndEmptyTitles() {
        assertNull(RadioTitle.parse("Rock FM", null, "rock fm"))
        assertNull(RadioTitle.parse("  ", null, "Rock FM"))
        assertNull(RadioTitle.parse(null, null, "Rock FM"))
        assertNull(RadioTitle.parse("Rock FM - Your station", null, "Rock FM"))
        assertNull(RadioTitle.parse("News at noon", null, "Rock FM"))
    }

    @Test fun serverRule() {
        assertTrue(Scrobblers.isAllowedServer("https://api.listenbrainz.org"))
        assertTrue(Scrobblers.isAllowedServer("http://192.168.1.5:8100"))
        assertTrue(!Scrobblers.isAllowedServer("http://example.com"))
        assertTrue(!Scrobblers.isAllowedServer("ftp://example.com"))
    }
}
