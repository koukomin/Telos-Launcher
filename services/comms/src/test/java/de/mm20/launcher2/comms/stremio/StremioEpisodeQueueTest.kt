package de.mm20.launcher2.comms.stremio

import org.junit.Assert.*
import org.junit.Test

class StremioEpisodeQueueTest {
    @Test fun nextUsesCatalogOrderingAcrossSeasons() {
        val a = Video("a", "First", season = 1, episode = 1)
        val b = Video("b", "Second", season = 1, episode = 2)
        val c = Video("c", "Next season", season = 2, episode = 1)
        assertEquals(listOf(b, c), StremioEpisodeQueue.after("series", listOf(c, b, a, b), "a"))
    }
    @Test fun noGuessingForMoviesMissingIdsOrUnnumberedVideos() {
        val episode = Video("a", "First", season = 1, episode = 1)
        assertTrue(StremioEpisodeQueue.after("movie", listOf(episode), "a").isEmpty())
        assertTrue(StremioEpisodeQueue.after("series", listOf(episode), "unknown").isEmpty())
        assertTrue(StremioEpisodeQueue.after("series", listOf(Video("a", "Trailer")), "a").isEmpty())
    }
}
