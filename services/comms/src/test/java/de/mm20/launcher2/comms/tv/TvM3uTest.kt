package de.mm20.launcher2.comms.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvM3uTest {
    @Test fun parsesExtinf() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="x" tvg-logo="https://l.example/a.png" group-title="News, World",Channel, One
            https://s.example/a.m3u8
            #EXTVLCOPT:http-user-agent=foo
            #EXTINF:-1,No Attrs
            http://s.example/b.ts
            #EXTINF:-1,Bad
            rtsp://s.example/c
            #EXTINF:-1,Creds
            http://user:pw@s.example/d
            https://s.example/noinfo.m3u8
        """.trimIndent()
        val p = TvM3u.parse(text)
        assertEquals(3, p.entries.size)
        assertEquals(2, p.skipped)
        assertEquals("Channel, One", p.entries[0].name)
        assertEquals("News, World", p.entries[0].group)
        assertEquals("https://l.example/a.png", p.entries[0].logo)
        assertEquals("No Attrs", p.entries[1].name)
        assertEquals("s.example", p.entries[2].name)
    }

    @Test fun limitTruncates() {
        val text = (1..10).joinToString("\n") { "#EXTINF:-1,C$it\nhttps://s.example/$it" }
        val p = TvM3u.parse(text, maxEntries = 4)
        assertEquals(4, p.entries.size)
        assertTrue(p.truncated)
    }

    @Test fun urlValidation() {
        assertEquals("https://a.example/x", TvUrls.sanitize("  https://a.example/x "))
        assertEquals("http://192.168.0.2:8080/s", TvUrls.sanitize("http://192.168.0.2:8080/s"))
        assertNull(TvUrls.sanitize("ftp://a.example/x"))
        assertNull(TvUrls.sanitize("https://u:p@a.example/x"))
        assertNull(TvUrls.sanitize("javascript:alert(1)"))
        assertNull(TvUrls.sanitize("https://a.example/a b"))
        assertNull(TvUrls.sanitize(""))
        assertNull(TvUrls.sanitize(null))
        assertFalse(TvUrls.sanitize("https://" + "a".repeat(3000)) != null)
    }
}
