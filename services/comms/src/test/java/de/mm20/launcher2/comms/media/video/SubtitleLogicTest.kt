package de.mm20.launcher2.comms.media.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SubtitleLogicTest {
    private fun r(lang: String, release: String, downloads: Int = 0, hi: Boolean = false, hash: Boolean = false) =
        SubtitleResult("p", "$lang$release", lang, release, downloads, release, hi, hash)

    @Test fun hashOfZeroFileIsItsSize() {
        val size = 200_000L
        val h = OpenSubtitlesHash.compute(size) { off, buf -> buf.fill(0); buf.size }
        assertEquals("%016x".format(size), h)
    }

    @Test fun hashAddsWords() {
        val size = 65536L * 2
        // every byte 1: each 64 bit word is 0x0101010101010101, 8192 words per chunk, two chunks
        val h = OpenSubtitlesHash.compute(size) { _, buf -> buf.fill(1); buf.size }
        val expected = size + 2 * 8192 * 0x0101010101010101L
        assertEquals("%016x".format(expected), h)
    }

    @Test fun hashNeedsBigFile() = assertNull(OpenSubtitlesHash.compute(100) { _, b -> b.size })

    @Test fun languageOrderBeatsEverything() {
        val ranked = SubtitleRanking.rank(
            listOf(r("en", "Movie.2020.1080p", 99999, hash = true), r("el", "other", 1)),
            listOf("el", "en"), "Movie.2020.1080p.mkv",
        )
        assertEquals("el", ranked.first().language)
    }

    @Test fun hashAndSimilarityAndHi() {
        val file = "Show.S01E02.720p.WEB.x264-GRP.mkv"
        val ranked = SubtitleRanking.rank(
            listOf(
                r("en", "Show.S01E02.1080p.BluRay-XYZ", 500),
                r("en", "Show.S01E02.720p.WEB.x264-GRP", 10, hi = true),
                r("en", "Show.S01E02.720p.WEB.x264-GRP", 10),
            ), listOf("en"), file,
        )
        assertEquals(false, ranked.first().hearingImpaired)
        assertEquals(true, ranked.last().release.contains("1080p") || ranked.last().hearingImpaired)
        val withHash = SubtitleRanking.rank(listOf(r("en", "a", 5), r("en", "b", 5, hash = true)), listOf("en"), "x")
        assertEquals("b", withHash.first().release)
    }

    @Test fun languageCodes() {
        assertEquals("ell", SubtitleLanguages.toOpenSubtitles("el"))
        assertEquals("eng", SubtitleLanguages.toOpenSubtitles("en"))
        assertEquals("ger", SubtitleLanguages.toOpenSubtitles("de"))
        assertEquals("por", SubtitleLanguages.toOpenSubtitles("pt-BR"))
        assertEquals(listOf("el", "en", "pt"), SubtitleLanguages.parseList("el, EN,pt-BR,el"))
        assertEquals(listOf("en"), SubtitleLanguages.parseList(""))
    }

    @Test fun legacyUrls() {
        val q = SubtitleQuery("The Show", 1, 2, null, listOf("en"), "x.mkv", "8e245d9679d31e12", 12909756)
        val urls = OpenSubtitlesLegacyProvider.legacyUrls(q, "eng,ell")
        assertEquals("https://rest.opensubtitles.org/search/moviebytesize-12909756/moviehash-8e245d9679d31e12/sublanguageid-eng,ell", urls[0])
        assertEquals("https://rest.opensubtitles.org/search/episode-2/query-the%20show/season-1/sublanguageid-eng,ell", urls[1])
    }

    @Test fun gunzipAndZip() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nHi\n".toByteArray()
        val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(text) } }.toByteArray()
        assertEquals(String(text), String(SubtitleArchive.extract(gz).second))
        val zip = ByteArrayOutputStream().also { o ->
            ZipOutputStream(o).use { z ->
                z.putNextEntry(ZipEntry("readme.txt")); z.write("x".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("dir/movie.srt")); z.write(text); z.closeEntry()
            }
        }.toByteArray()
        val (name, data) = SubtitleArchive.extract(zip)
        assertEquals("movie.srt", name)
        assertEquals(String(text), String(data))
    }

    @Test fun charsets() {
        assertEquals("Καλημέρα", SubtitleText.decode("Καλημέρα".toByteArray(Charsets.UTF_8), "el", null))
        assertEquals("Καλημέρα", SubtitleText.decode("Καλημέρα".toByteArray(charset("windows-1253")), "el", null))
        assertEquals("Привет", SubtitleText.decode("Привет".toByteArray(charset("windows-1251")), "ru", "windows-1251"))
        assertEquals("héllo", SubtitleText.decode("héllo".toByteArray(charset("windows-1252")), "fr", null))
        assertEquals("ab", SubtitleText.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'a'.code.toByte(), 0, 'b'.code.toByte(), 0), null, null))
        assertEquals("a\nb", SubtitleText.decode("﻿a\r\nb".toByteArray(), null, null))
    }

    @Test fun shiftSrt() {
        val src = "1\n00:00:01,500 --> 00:01:02,000\nHi\n"
        assertEquals("1\n00:00:02,000 --> 00:01:02,500\nHi\n", SubtitleShift.shift(src, "srt", 500))
        assertEquals("1\n00:00:00,000 --> 00:01:00,500\nHi\n", SubtitleShift.shift(src, "srt", -1500))
    }

    @Test fun shiftVttAndAss() {
        assertEquals("00:02.000 --> 00:03.500 line:0", SubtitleShift.shift("00:01.000 --> 00:02.500 line:0", "vtt", 1000))
        val ass = "Dialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,Hello, world"
        assertEquals("Dialogue: 0,0:00:03.00,0:00:04.50,Default,,0,0,0,,Hello, world", SubtitleShift.shift(ass, "ass", 2000))
        assertTrue(SubtitleShift.shift(ass, "ass", -5000).contains("0:00:00.00,0:00:00.00"))
    }
}
