package de.mm20.launcher2.wallpapers

import org.junit.Assert.*
import org.junit.Test

class VideoGlassTest {
    @Test fun readbackIsBoundedAndPreservesAspectRatio() {
        assertEquals(96 to 192, videoGlassSize(2000, 4000))
        assertEquals(192 to 96, videoGlassSize(4000, 2000))
        assertEquals(16 to 8, videoGlassSize(16, 8))
        assertEquals(1 to 192, videoGlassSize(1, Int.MAX_VALUE))
        assertThrows(IllegalArgumentException::class.java) { videoGlassSize(0, 200) }
    }
    @Test fun lifecycleDemandsAreDeduplicatedBoundedAndRemoved() {
        val glass = VideoGlass()
        val first = Any()
        val second = Any()
        val request = VideoGlassRequest(32, 400, 800)
        glass.register(first, listOf(request, request))
        glass.register(second, listOf(request))
        assertEquals(listOf(request), glass.requests.value)
        glass.unregister(first)
        assertEquals(listOf(request), glass.requests.value)
        glass.unregister(second)
        assertTrue(glass.requests.value.isEmpty())
        repeat(20) { glass.register(Any(), listOf(request.copy(radius = it + 1))) }
        assertEquals(4, glass.requests.value.size)
        assertEquals(20, glass.requests.value.first().radius)
    }
    @Test fun disabledAndInvalidProfilesNeverStartSampling() {
        val glass = VideoGlass()
        glass.register(Any(), listOf(VideoGlassRequest(0, 400, 800), VideoGlassRequest(32, 0, 800)))
        assertTrue(glass.requests.value.isEmpty())
    }
}
