package de.mm20.launcher2.comms.stremio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.io.ByteArrayOutputStream

class StremioWatchedTest {
    private fun field(anchor: String, length: Int, vararg bytes: Int): String {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(bytes.map { b -> b.toByte() }.toByteArray()) }
        return "$anchor:$length:" + Base64.getEncoder().encodeToString(out.toByteArray())
    }

    @Test fun preservesEpisodeIdsContainingColonsAndBitOrder() {
        val ids = (1..10).map { "tt123:1:$it" }
        val state = StremioWatched(videos = field(ids[9], 10, 5, 2))
        assertEquals(setOf(ids[0], ids[2], ids[9]), state.episodes(ids))
    }

    @Test fun alignsToAnchorWhenEarlierEpisodesAreAdded() {
        val state = StremioWatched(videos = field("tt123:1:3", 3, 5))
        assertEquals(setOf("tt123:1:1", "tt123:1:3"), state.episodes((0..4).map { "tt123:1:$it" }))
    }

    @Test fun missingAnchorAndMalformedDataDoNotMarkAnythingWatched() {
        assertTrue(StremioWatched(videos = field("missing", 1, 255)).episodes(listOf("tt1")).isEmpty())
        for (value in listOf("invalid", "tt1:x:AAAA", "tt1:1:invalid!", "tt1:1000001:AAAA")) {
            assertTrue(StremioWatched(videos = value).episodes(listOf("tt1")).isEmpty())
        }
        assertTrue(StremioWatched(movie = true).episodes(listOf("tt1")).isEmpty())
    }
}
