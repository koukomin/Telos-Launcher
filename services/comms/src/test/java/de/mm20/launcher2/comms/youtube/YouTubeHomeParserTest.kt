package de.mm20.launcher2.comms.youtube

import org.junit.Assert.*
import org.junit.Test

class YouTubeHomeParserTest {
    @Test fun bracesInsideTitlesDoNotTruncateInitialData() {
        val data = """{"videoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"A } B { C"}}}"""
        assertEquals(data, initialYouTubeData("<script>var ytInitialData = $data;</script>"))
        assertNull(initialYouTubeData("ytInitialData = {\"broken\":"))
        assertEquals("A } B { C", homeVideoEntries(data).single().title)
    }
    @Test fun modernAndClassicCardsAreDeduplicatedAndNonVideosExcluded() {
        val data = """{"items":[{"lockupViewModel":{"contentId":"abcdefghijk","metadata":{"lockupMetadataViewModel":{"title":{"content":"Modern"}}}}},{"videoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"Duplicate"}}},{"lockupViewModel":{"contentId":"PLplaylist","metadata":{"lockupMetadataViewModel":{"title":{"content":"Not a video"}}}}}]}"""
        assertEquals(listOf("Modern"), homeVideoEntries(data).map { it.title })
    }
    @Test fun musicHomeSongsAndLiveClientVersionAreRead() {
        val data = """{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Song"}]},"navigationEndpoint":{"watchEndpoint":{"videoId":"abcdefghijk"}}}}"""
        assertEquals("Song", homeVideoEntries(data).single().title)
        assertEquals("1.20261025.01.00", musicClientVersionFromPage("""{"INNERTUBE_CLIENT_VERSION":"1.20261025.01.00"}"""))
    }
    @Test fun extractorMixCookiesSurviveAuthenticatedRequests() {
        val result = YouTubeSessionPolicy.mergeCookies("VISITOR_INFO1_LIVE=mix; SAPISID=wrong", "SAPISID=real; SID=private; VISITOR_INFO1_LIVE=old")
        assertTrue(result.contains("VISITOR_INFO1_LIVE=mix"))
        assertTrue(result.contains("SAPISID=real"))
        assertFalse(result.contains("wrong"))
    }
}
