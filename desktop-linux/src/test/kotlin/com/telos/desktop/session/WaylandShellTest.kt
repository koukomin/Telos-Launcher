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
        assertEquals("W2", WaylandShell.PHASE)
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

    @Test
    fun parsesLayerClick() {
        assertEquals("ready", LayerShellParser.eventId("""{"event":"ready"}"""))
        assertEquals("kickoff", LayerShellParser.eventId("""{"event":"click","id":"kickoff"}"""))
        assertNull(LayerShellParser.eventId("nope"))
    }

    @Test
    fun parsesPortalOpenRequest() {
        val req = PortalRequestParser.parse("""{"id":"abc","mode":"open","title":"Open File","directory":false}""")
        assertEquals("abc", req?.id)
        assertEquals("Open File", req?.title)
        assertEquals(false, req?.directory)
    }

    @Test
    fun parsesSniSnapshot() {
        val items = SniTrayParser.parseItems("""{"items":[{"id":"nm","title":"Network","service":":1.12"}]}""")
        assertEquals(1, items.size)
        assertEquals("nm", items[0].id)
        assertEquals("Network", items[0].title)
    }
}
