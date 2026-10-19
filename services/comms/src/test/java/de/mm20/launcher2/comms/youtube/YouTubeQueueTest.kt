package de.mm20.launcher2.comms.youtube

import org.junit.Assert.*
import org.junit.Test

class YouTubeQueueTest {
    @Test fun queueRoundTripPreservesProviderIdentityAndUnicodeMetadata() {
        val entries = listOf(YouTubeEntry("https://youtube.com/watch?v=a", "Μουσική 🎶", "歌手", null, 180),
            YouTubeEntry("https://youtube.com/watch?v=b", "Second", "Artist", "https://image.example/b.jpg", 240))
        assertEquals(entries, YouTubeQueue.decode(YouTubeQueue.encode(entries)))
    }
    @Test fun missingOrCorruptQueueIsSafe() {
        assertTrue(YouTubeQueue.decode(null).isEmpty())
        assertTrue(YouTubeQueue.decode("{").isEmpty())
    }
    @Test fun queueIsBoundedBeforeSendingToMediaSession() {
        val item = YouTubeEntry("https://youtube.com/watch?v=a", "A", "B", null, 1)
        assertEquals(500, YouTubeQueue.decode(YouTubeQueue.encode(List(501) { item })).size)
    }
}
