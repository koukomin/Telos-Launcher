package de.mm20.launcher2.comms.stremio

import org.junit.Assert.*
import org.junit.Test

class StremioStreamFilterTest {
    private fun stream(name: String, bytes: Long? = null) = StreamItem("source", name = name, url = "https://example.com/video", sizeBytes = bytes)
    @Test fun knownResolutionIsFilteredButUnknownRemainsAvailable() {
        val hd = stream("Movie 1080p")
        val unknown = stream("Movie")
        assertEquals(listOf(hd, unknown), StremioStreamFilter.apply(listOf(stream("Movie 720p"), hd, unknown), StremioPlaybackSettings(resolution = 1080)))
    }
    @Test fun sizeUsesLongMetadataAndScreenersAreOptional() {
        val normal = stream("Movie 1080p", 2L * 1024 * 1024 * 1024)
        val streams = listOf(normal, stream("Movie HDCAM"), stream("Movie 1080p 20 GB"))
        assertEquals(listOf(normal), StremioStreamFilter.apply(streams, StremioPlaybackSettings(maxSizeGb = 5, hideScreeners = true)))
        assertEquals(streams, StremioStreamFilter.apply(streams, StremioPlaybackSettings()))
    }
    @Test fun manualBrowsingFallsBackButAutoplayDoesNotIgnorePreferences() {
        val streams = listOf(stream("Movie 720p"))
        val settings = StremioPlaybackSettings(resolution = 1080)
        assertEquals(streams, StremioStreamFilter.apply(streams, settings))
        assertTrue(StremioStreamFilter.apply(streams, settings, fallback = false).isEmpty())
    }
    @Test fun tsContainerDoesNotMakeALegitimateStreamAScreener() {
        val streams = listOf(stream("Documentary 1080p.ts"))
        assertEquals(streams, StremioStreamFilter.apply(streams, StremioPlaybackSettings(hideScreeners = true), fallback = false))
    }
}
