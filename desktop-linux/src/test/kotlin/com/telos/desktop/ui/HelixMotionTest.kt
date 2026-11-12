package com.telos.desktop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelixMotionTest {
    @Test
    fun drawerMotionIsYakuakeSpeedNotFullscreenFadeOnly() {
        assertEquals(180, HelixMotion.DrawerMs)
        assertTrue(HelixMotion.DrawerMs >= HelixMotion.OverlayMs)
        // Presence of dedicated drawer transitions (compile-time API surface).
        assertTrue(HelixMotion.drawerEnter != HelixMotion.enter)
        assertTrue(HelixMotion.drawerExit != HelixMotion.exit)
    }
}
