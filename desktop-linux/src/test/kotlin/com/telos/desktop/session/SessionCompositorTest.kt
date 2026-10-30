package com.telos.desktop.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCompositorTest {
    @Test
    fun prefersNiriWhenAsked() {
        assertEquals("niri", SessionCompositor.resolve("niri", hasLabwc = true, hasNiri = true))
        assertEquals("labwc", SessionCompositor.resolve("niri", hasLabwc = true, hasNiri = false))
        assertEquals("labwc", SessionCompositor.resolve(null, hasLabwc = true, hasNiri = true))
        assertEquals("niri", SessionCompositor.resolve("labwc", hasLabwc = false, hasNiri = true))
    }

    @Test
    fun fractionalStops() {
        assertEquals("150%", SessionCompositor.scaleLabel(1.5))
        assertEquals(1.5, SessionCompositor.nearestScale(1.4), 0.0)
        assertEquals(2.0, SessionCompositor.nearestScale(1.9), 0.0)
    }
}
