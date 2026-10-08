package de.mm20.launcher2.downloads

import de.mm20.launcher2.downloads.engine.RateLimiter
import de.mm20.launcher2.downloads.logic.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class MiscLogicTest {
    @Test fun rangeHeader() {
        assertEquals("bytes=0-99", HttpRanges.rangeHeader(0, 99))
        assertEquals("bytes=500-", HttpRanges.rangeHeader(500, -1))
    }

    @Test fun contentRangeTotal() {
        assertEquals(12345L, HttpRanges.totalFromContentRange("bytes 0-0/12345"))
        assertEquals(0L, HttpRanges.totalFromContentRange("bytes */0"))
        assertNull(HttpRanges.totalFromContentRange("bytes 0-0/*"))
        assertNull(HttpRanges.totalFromContentRange(null))
        assertNull(HttpRanges.totalFromContentRange("garbage"))
    }

    @Test fun ifRange() {
        assertEquals("\"abc\"", HttpRanges.ifRangeValue("\"abc\"", "date"))
        assertEquals("date", HttpRanges.ifRangeValue("W/\"abc\"", "date"))
        assertNull(HttpRanges.ifRangeValue(null, null))
    }

    @Test fun changedDetection() {
        assertFalse(HttpRanges.changed(10, 10, "a", "a", "m", "m"))
        assertTrue(HttpRanges.changed(10, 11, null, null, null, null))
        assertTrue(HttpRanges.changed(10, 10, "a", "b", null, null))
        assertTrue(HttpRanges.changed(10, 10, null, null, "m1", "m2"))
        assertFalse(HttpRanges.changed(10, 10, "a", null, null, null))
        assertFalse(HttpRanges.changed(-1, 10, null, null, null, null))
    }

    @Test fun checksumParse() {
        val md5 = "d41d8cd98f00b204e9800998ecf8427e"
        assertEquals(ExpectedChecksum("MD5", md5), Checksums.parse(md5))
        assertEquals(ExpectedChecksum("MD5", md5), Checksums.parse("MD5:" + md5.uppercase()))
        assertEquals("SHA-1", Checksums.parse("sha1=" + "a".repeat(40))!!.algorithm)
        assertEquals("SHA-256", Checksums.parse("sha-256:" + "b".repeat(64))!!.algorithm)
        assertNull(Checksums.parse("sha256:" + "b".repeat(40)))
        assertNull(Checksums.parse("zz"))
        assertNull(Checksums.parse(""))
        assertNull(Checksums.parse("crc32:abcdef12"))
    }

    @Test fun checksumCompute() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Checksums.compute("MD5", ByteArrayInputStream(ByteArray(0))))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Checksums.compute("SHA-1", ByteArrayInputStream("abc".toByteArray())))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Checksums.compute("SHA-256", ByteArrayInputStream("abc".toByteArray())),
        )
    }

    @Test fun linkParser() {
        val text = "Get https://a.org/x.zip, and (http://b.org/y?z=1). Again https://a.org/x.zip\nftp://no.org/f magnet:?xt=urn:btih:1"
        assertEquals(listOf("https://a.org/x.zip", "http://b.org/y?z=1"), LinkParser.extractHttp(text))
        assertTrue(LinkParser.isHttpUrl("https://a.org/f"))
        assertFalse(LinkParser.isHttpUrl("a https://a.org/f"))
        assertTrue(LinkParser.extractHttp("nothing").isEmpty())
    }

    @Test fun categories() {
        assertEquals(DownloadCategory.Video, MimeTypes.categoryOf("a.mkv", null))
        assertEquals(DownloadCategory.Audio, MimeTypes.categoryOf("a", "audio/mpeg"))
        assertEquals(DownloadCategory.Documents, MimeTypes.categoryOf("a.pdf", null))
        assertEquals(DownloadCategory.Archives, MimeTypes.categoryOf("a.zip", "application/zip"))
        assertEquals(DownloadCategory.Programs, MimeTypes.categoryOf("a.apk", "application/vnd.android.package-archive"))
        assertEquals(DownloadCategory.Other, MimeTypes.categoryOf("a.bin", "application/octet-stream"))
    }

    @Test fun formatting() {
        assertEquals("512 B", Formatting.size(512))
        assertEquals("1.5 KB", Formatting.size(1536, java.util.Locale.ROOT))
        assertEquals("?", Formatting.size(-1))
        assertEquals("1:15", Formatting.duration(75))
        assertEquals("1:01:40", Formatting.duration(3700))
    }

    @Test fun rateLimiterSpacesRequests() {
        var now = 0L
        val l = RateLimiter { now }
        assertEquals(0L, l.reserve(1000)) // unlimited
        l.bytesPerSecond = 1000
        assertEquals(0L, l.reserve(1000))              // first one is free, books one second
        assertEquals(1_000_000_000L, l.reserve(1000))  // second waits for it
        assertEquals(2_000_000_000L, l.reserve(500))
        now = 10_000_000_000L
        assertEquals(0L, l.reserve(100))               // idle time is not banked as a burst allowance
        assertEquals(100_000_000L, l.reserve(100))
    }

    @Test fun taskProgressAndEta() {
        val t = DownloadTask(id = "1", url = "u", state = DownloadState.Downloading, totalBytes = 1000, downloadedBytes = 500, speedBps = 50)
        assertEquals(0.5f, t.progress, 0.001f)
        assertEquals(10L, t.etaSeconds)
        assertNull(t.copy(speedBps = 0).etaSeconds)
    }
}
