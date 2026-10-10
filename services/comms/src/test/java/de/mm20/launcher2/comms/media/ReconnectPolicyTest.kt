package de.mm20.launcher2.comms.media

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    private var now = 0L
    private fun policy() = ReconnectPolicy(clock = { now })

    @Test
    fun backoffGrowsAndCaps() {
        val p = policy()
        val delays = (1..8).map { p.onError(ReconnectPolicy.Kind.NETWORK); p.delayMs(true) }
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 15000L, 15000L, 15000L, 15000L), delays)
    }

    @Test
    fun noRetryWhileOffline() {
        val p = policy()
        p.onError(ReconnectPolicy.Kind.NETWORK)
        assertNull(p.delayMs(false))
    }

    @Test
    fun networkErrorsNeverGiveUp() {
        val p = policy()
        repeat(100) { assertTrue(p.onError(ReconnectPolicy.Kind.NETWORK)) }
    }

    @Test
    fun otherErrorsStopAfterThreeRetries() {
        val p = policy()
        assertTrue(p.onError(ReconnectPolicy.Kind.OTHER))
        assertTrue(p.onError(ReconnectPolicy.Kind.OTHER))
        assertTrue(p.onError(ReconnectPolicy.Kind.OTHER))
        assertFalse(p.onError(ReconnectPolicy.Kind.OTHER))
    }

    @Test
    fun stablePlaybackResetsBackoff() {
        val p = policy()
        repeat(4) { p.onError(ReconnectPolicy.Kind.NETWORK) }
        p.onReady()
        now += 31_000
        p.onError(ReconnectPolicy.Kind.NETWORK)
        assertEquals(1000L, p.delayMs(true))
    }

    @Test
    fun flappingKeepsBackoff() {
        val p = policy()
        repeat(2) { p.onError(ReconnectPolicy.Kind.NETWORK) }
        p.onReady()
        now += 2_000
        p.onError(ReconnectPolicy.Kind.NETWORK)
        assertEquals(4000L, p.delayMs(true))
    }

    @Test
    fun classification() {
        val k = ReconnectPolicy.Companion
        assertEquals(ReconnectPolicy.Kind.NETWORK, k.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, false))
        assertEquals(ReconnectPolicy.Kind.NETWORK, k.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null, false))
        assertEquals(ReconnectPolicy.Kind.NETWORK, k.classify(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW, null, false))
        assertEquals(ReconnectPolicy.Kind.NETWORK, k.classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 503, true))
        assertEquals(ReconnectPolicy.Kind.OTHER, k.classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 404, true))
        assertEquals(ReconnectPolicy.Kind.OTHER, k.classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403, true))
        assertEquals(ReconnectPolicy.Kind.NETWORK, k.classify(PlaybackException.ERROR_CODE_UNSPECIFIED, null, true))
        assertEquals(ReconnectPolicy.Kind.OTHER, k.classify(PlaybackException.ERROR_CODE_UNSPECIFIED, null, false))
        assertEquals(ReconnectPolicy.Kind.OTHER, k.classify(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, null, false))
        assertEquals(ReconnectPolicy.Kind.OTHER, k.classify(PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED, null, false))
    }
}
