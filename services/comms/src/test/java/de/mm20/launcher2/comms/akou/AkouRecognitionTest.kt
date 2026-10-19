package de.mm20.launcher2.comms.akou

import org.junit.Assert.*
import org.junit.Test

class AkouRecognitionTest {
    @Test fun actualProviderResultUsesReturnedMetadata() {
        val song = AkouRecognition.parseResponse("""{"matches":[{"id":"1"}],"track":{"title":"Song","subtitle":"Artist","sections":[{"metadata":[{"title":"Album","text":"Album"}]}]}}""")!!
        assertEquals("Song", song.title)
        assertEquals("Artist", song.artist)
        assertEquals("Album", song.album)
        assertTrue(song.recognizedAt > 0)
    }
    @Test fun noMatchRemainsNoMatch() {
        assertNull(AkouRecognition.parseResponse("""{"matches":[]}"""))
    }
    @Test fun providerFailureNeverBecomesASong() {
        assertThrows(IllegalStateException::class.java) { AkouRecognition.parseResponse("""{"error":"rate limited"}""") }
    }
    @Test fun invalidMetadataNeverBecomesASong() {
        assertThrows(IllegalStateException::class.java) {
            AkouRecognition.parseResponse("""{"matches":[{}],"track":{"title":"  "}}""")
        }
    }
}
