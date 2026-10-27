package de.mm20.launcher2.comms.media.video.trakt

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TraktLibraryParserTest {
    @Test fun movieWatchlistUsesStableIdsAndListedTime() {
        val result = TraktLibraryParser.watchlist("""[{"listed_at":"2026-10-27T10:00:00Z","movie":{"title":"Same title","ids":{"imdb":"tt1234567","trakt":42}}}]""", "movie")
        assertEquals("tt1234567", result.single().id)
        assertEquals("movie", result.single().type)
        assertTrue(result.single().addedAt > 0)
    }

    @Test fun missingImdbRetainsTheCanonicalTraktIdentityInsteadOfGuessingByTitle() {
        val result = TraktLibraryParser.watchlist("""[{"show":{"title":"Show","ids":{"trakt":42}}}]""", "series")
        assertEquals("trakt:42", result.single().id)
    }

    @Test fun playbackUsesShowIdentityAndEpisodeNumbersNotTheEpisodeImdbAsSeriesId() {
        val result = TraktLibraryParser.playback("""[{"progress":25,"paused_at":"2026-10-27T10:00:00Z","show":{"title":"Show","ids":{"imdb":"tt1234567"}},"episode":{"title":"Episode","season":0,"number":2,"runtime":40,"ids":{"imdb":"tt9876543"}}}]""").single()
        assertEquals("tt1234567", result.id)
        assertEquals("tt1234567:0:2", result.videoId)
        assertEquals(600000L, result.positionMs)
        assertEquals(25.0, result.resumePercent!!, 0.001)
        assertEquals(0L, result.timeWatchedMs)
        assertFalse(result.completed)
    }

    @Test fun missingRuntimeStillRetainsAnAccuratePercentageForTheActualPlayerDuration() {
        val p = TraktLibraryParser.playback("""[{"progress":33.3,"movie":{"title":"Film","ids":{"imdb":"tt1234567"}}}]""").single()
        assertEquals(0L, p.durationMs)
        assertEquals(33.3, p.resumePercent!!, 0.001)
    }

    @Test fun unsupportedAndInvalidPlaybackCannotProduceAResumeTarget() {
        val result = TraktLibraryParser.playback("""[{"progress":100,"movie":{"ids":{"imdb":"tt1234567"}}},{"progress":-1,"movie":{"ids":{"imdb":"tt1234567"}}},{"progress":10,"show":{"ids":{"trakt":42}},"episode":{"season":1}}]""")
        assertTrue(result.isEmpty())
    }

    @Test fun cacheCannotHydrateIntoAnotherAuthorizationGeneration() {
        val original = TraktLibraryState(library = TraktLibraryParser.watchlist("""[{"movie":{"title":"Film","ids":{"imdb":"tt1234567"}}}]""", "movie"), refreshedAt = 1234)
        val encoded = TraktLibraryParser.encode(original, "account-a")
        assertEquals(original, TraktLibraryParser.decode(encoded, "account-a"))
        assertNull(TraktLibraryParser.decode(encoded, "account-b"))
    }

    @Test fun writesAcceptOnlyTypedCanonicalIdentifiers() {
        assertEquals("tt1234567", TraktLibraryParser.identifiers("tt1234567")!!.getString("imdb"))
        assertEquals(42L, TraktLibraryParser.identifiers("trakt:42")!!.getLong("trakt"))
        assertEquals(99L, TraktLibraryParser.identifiers("tmdb:99")!!.getLong("tmdb"))
        for (value in listOf("Same title", "tt1234567;ignored", "trakt:-1", "trakt:0", "tmdb:999999999999999999999999")) {
            assertNull(TraktLibraryParser.identifiers(value))
        }
    }
}
