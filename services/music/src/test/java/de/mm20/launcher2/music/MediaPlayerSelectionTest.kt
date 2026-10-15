package de.mm20.launcher2.music

import org.junit.Assert.*
import org.junit.Test

class MediaPlayerSelectionTest {
    private data class Session(val name: String, val playing: Boolean, val posted: Long)
    @Test fun internalSessionsDoNotNeedAnExternalMusicActivity() {
        assertEquals(setOf("poweramp", "telos"), eligibleMusicPackages(setOf("poweramp"), "telos", emptySet(), emptySet()))
    }
    @Test fun explicitDenyWinsForInternalAndAllowedApps() {
        assertEquals(setOf("poweramp"), eligibleMusicPackages(setOf("poweramp"), "telos", setOf("custom"), setOf("telos", "custom")))
    }
    @Test fun currentlyPlayingBeatsANewerPauseNotification() {
        val radio = Session("radio", true, 10)
        assertEquals(radio, selectPlaybackCandidate(listOf(radio, Session("music", false, 20)), { it.playing }, { it.posted }))
    }
    @Test fun mostRecentPlayerWinsWhenBothPaused() {
        val recent = Session("poweramp", false, 20)
        assertEquals(recent, selectPlaybackCandidate(listOf(Session("radio", false, 10), recent), { it.playing }, { it.posted }))
    }
    @Test fun emptySessionsHaveNoPlayer() {
        assertNull(selectPlaybackCandidate(emptyList<Session>(), { it.playing }, { it.posted }))
    }
}
