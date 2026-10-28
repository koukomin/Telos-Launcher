package de.mm20.launcher2.comms.media.video.trakt

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TraktReplayPolicyTest {
    @Test fun replayUsesTheRequestedIdNamespaceAndExactEpisode() {
        val raw = """[{"show":{"ids":{"imdb":"tt1234567","trakt":42}},"seasons":[{"number":1,"episodes":[{"number":2,"last_watched_at":"2026-10-20T12:00:00Z"},{"number":3,"last_watched_at":"2026-10-21T12:00:00Z"}]}]}]"""
        val at = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli()
        assertEquals(at, TraktReplayPolicy.latestWatchedAt(raw, "trakt:42", 1, 2))
        assertEquals(0L, TraktReplayPolicy.latestWatchedAt(raw, "tt9999999", 1, 2))
        assertEquals(0L, TraktReplayPolicy.latestWatchedAt(raw, "tt1234567", 1, 4))
    }
    @Test fun newerEpisodeResumeSupersedesAnOlderPauseFromAnotherDevice() {
        val raw = """[{"show":{"ids":{"imdb":"tt1234567","trakt":42}},"episode":{"season":2,"number":1},"paused_at":"2026-10-21T12:00:00Z"}]"""
        assertTrue(TraktReplayPolicy.newerPlayback(raw, "trakt:42", 1, Instant.parse("2026-10-20T12:00:00Z").toEpochMilli()))
        assertFalse(TraktReplayPolicy.newerPlayback(raw, "trakt:42", 1, Instant.parse("2026-10-22T12:00:00Z").toEpochMilli()))
        assertFalse(TraktReplayPolicy.newerPlayback(raw, "tt9999999", 1, 0))
    }
}
