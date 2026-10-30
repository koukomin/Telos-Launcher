package com.telos.desktop.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WaylandShellTest {
    @Test
    fun exclusiveZonesMatchPanelAndDock() {
        assertEquals(36, WaylandShell.PANEL_PX)
        assertEquals(56, WaylandShell.DOCK_PX)
        assertEquals("W0", WaylandShell.PHASE)
    }

    @Test
    fun parsesNotifyJsonLine() {
        val hit = FreedesktopNotifyParser.parseLine("""{"summary":"Firefox","body":"Download done"}""")
        assertEquals("Firefox" to "Download done", hit)
    }

    @Test
    fun parsesEscapedQuotes() {
        val hit = FreedesktopNotifyParser.parseLine("""{"summary":"Say \"hi\"","body":""}""")
        assertEquals("Say \"hi\"", hit?.first)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(FreedesktopNotifyParser.parseLine("not json"))
        assertNull(FreedesktopNotifyParser.parseLine(""))
    }

    @Test
    fun emptyPasswordIsNotPamSuccess() {
        assertFalse(PamAuth.authenticate(""))
    }
}
