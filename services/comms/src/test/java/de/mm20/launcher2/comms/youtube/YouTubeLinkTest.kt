package de.mm20.launcher2.comms.youtube

import de.mm20.launcher2.comms.youtube.flow.*
import org.junit.Assert.*
import org.junit.Test

class YouTubeLinkTest {
    @Test fun sharedMusicAndVideoLinksRetainTheirMode() {
        assertEquals(YouTubeLink.Video("dQw4w9WgXcQ", true), parseYouTubeLink("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(YouTubeLink.Video("dQw4w9WgXcQ", false), parseYouTubeLink("Watch https://youtu.be/dQw4w9WgXcQ!"))
    }
    @Test fun lookalikeDomainsAndInvalidVideosAreRejected() {
        assertNull(parseYouTubeLink("https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ"))
        assertNull(parseYouTubeLink("https://www.youtube.com/watch?v=bad"))
    }
    @Test fun audioLanguageMatchesUseTagsRatherThanDisplayNameSubstrings() {
        assertTrue(AudioStreamSelector.languageMatches("el-GR", "el"))
        assertFalse(AudioStreamSelector.languageMatches("fr", "en"))
        assertFalse(AudioStreamSelector.languageMatches("Chinese", "hi"))
    }
}
