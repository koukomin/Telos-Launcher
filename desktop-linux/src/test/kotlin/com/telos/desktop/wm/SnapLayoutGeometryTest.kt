package com.telos.desktop.wm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapLayoutGeometryTest {
    private val area = WorkArea.of(1920f, 1080f, topBarHeight = 36f, bottomDockHeight = 56f)

    @Test
    fun halfFiftyFifty() {
        val left = SnapLayoutGeometry.rect(SnapLayout.HalfLeft, area)
        val right = SnapLayoutGeometry.rect(SnapLayout.HalfRight, area)
        assertEquals(area.minX, left.x, 0.01f)
        assertEquals(area.minY, left.y, 0.01f)
        assertEquals(area.width / 2f, left.width, 0.01f)
        assertEquals(area.height, left.height, 0.01f)
        assertEquals(left.x + left.width, right.x, 0.01f)
        assertEquals(area.width / 2f, right.width, 0.01f)
        assertEquals(area.width, left.width + right.width, 0.01f)
    }

    @Test
    fun twoThirdsOneThird() {
        val wide = SnapLayoutGeometry.rect(SnapLayout.TwoThirdsLeft, area)
        val narrow = SnapLayoutGeometry.rect(SnapLayout.OneThirdRight, area)
        assertEquals(area.width * SnapLayoutGeometry.TWO_THIRDS, wide.width, 0.5f)
        assertEquals(area.width * SnapLayoutGeometry.ONE_THIRD, narrow.width, 0.5f)
        assertEquals(wide.x + wide.width, narrow.x, 0.5f)
        assertEquals(area.width, wide.width + narrow.width, 0.5f)

        val narrowLeft = SnapLayoutGeometry.rect(SnapLayout.OneThirdLeft, area)
        val wideRight = SnapLayoutGeometry.rect(SnapLayout.TwoThirdsRight, area)
        assertEquals(narrowLeft.x + narrowLeft.width, wideRight.x, 0.5f)
        assertEquals(area.width, narrowLeft.width + wideRight.width, 0.5f)
    }

    @Test
    fun gridTwoByTwo() {
        val tl = SnapLayoutGeometry.rect(SnapLayout.GridTopLeft, area)
        val tr = SnapLayoutGeometry.rect(SnapLayout.GridTopRight, area)
        val bl = SnapLayoutGeometry.rect(SnapLayout.GridBottomLeft, area)
        val br = SnapLayoutGeometry.rect(SnapLayout.GridBottomRight, area)
        val halfW = area.width / 2f
        val halfH = area.height / 2f
        assertEquals(halfW, tl.width, 0.01f)
        assertEquals(halfH, tl.height, 0.01f)
        assertEquals(tl.x + tl.width, tr.x, 0.01f)
        assertEquals(tl.y + tl.height, bl.y, 0.01f)
        assertEquals(br.x, tr.x, 0.01f)
        assertEquals(br.y, bl.y, 0.01f)
        assertEquals(area.width, tl.width + tr.width, 0.01f)
        assertEquals(area.height, tl.height + bl.height, 0.01f)
    }

    @Test
    fun maximizeFillsWorkArea() {
        val max = SnapLayoutGeometry.rect(SnapLayout.Maximize, area)
        assertEquals(area.minX, max.x, 0.01f)
        assertEquals(area.minY, max.y, 0.01f)
        assertEquals(area.width, max.width, 0.01f)
        assertEquals(area.height, max.height, 0.01f)
    }

    @Test
    fun cycleLeftAndRight() {
        assertEquals(SnapLayout.HalfLeft, SnapLayoutGeometry.cycleLeft(null))
        assertEquals(SnapLayout.TwoThirdsLeft, SnapLayoutGeometry.cycleLeft(SnapLayout.HalfLeft))
        assertEquals(SnapLayout.OneThirdLeft, SnapLayoutGeometry.cycleLeft(SnapLayout.TwoThirdsLeft))
        assertEquals(SnapLayout.HalfLeft, SnapLayoutGeometry.cycleLeft(SnapLayout.OneThirdLeft))

        assertEquals(SnapLayout.HalfRight, SnapLayoutGeometry.cycleRight(null))
        assertEquals(SnapLayout.TwoThirdsRight, SnapLayoutGeometry.cycleRight(SnapLayout.HalfRight))
        assertEquals(SnapLayout.OneThirdRight, SnapLayoutGeometry.cycleRight(SnapLayout.TwoThirdsRight))
        assertEquals(SnapLayout.HalfRight, SnapLayoutGeometry.cycleRight(SnapLayout.OneThirdRight))
    }

    @Test
    fun legacyZoneRoundTrip() {
        for (zone in listOf(
            SnapZone.Left, SnapZone.Right, SnapZone.Maximize,
            SnapZone.TopLeft, SnapZone.TopRight, SnapZone.BottomLeft, SnapZone.BottomRight,
        )) {
            val layout = SnapLayoutGeometry.fromLegacyZone(zone)!!
            assertEquals(zone, SnapLayoutGeometry.toLegacyZone(layout))
        }
        assertTrue(SnapLayoutGeometry.fromLegacyZone(SnapZone.None) == null)
    }

    @Test
    fun windowManagerAppliesLayoutGeometry() {
        val wm = TelosWindowManager()
        wm.setScreen(1920f, 1080f)
        val win = wm.open("Test", x = 100f, y = 80f, w = 400f, h = 300f)
        wm.applySnapLayout(win.id, SnapLayout.TwoThirdsLeft)
        val expected = SnapLayoutGeometry.rect(SnapLayout.TwoThirdsLeft, wm.workArea)
        assertEquals(expected.x, win.x, 0.5f)
        assertEquals(expected.y, win.y, 0.5f)
        assertEquals(expected.width, win.width, 0.5f)
        assertEquals(expected.height, win.height, 0.5f)
        assertEquals(SnapLayout.TwoThirdsLeft, win.layoutHint)
    }

    @Test
    fun niriActionsForLayouts() {
        assertEquals(
            listOf(listOf("set-window-width", "50%")),
            HostWmBridge.niriActions(SnapLayout.HalfLeft)?.map { it.toList() },
        )
        assertEquals(
            listOf(listOf("set-window-width", "66%")),
            HostWmBridge.niriActions(SnapLayout.TwoThirdsLeft)?.map { it.toList() },
        )
        assertEquals(
            listOf(listOf("maximize-column")),
            HostWmBridge.niriActions(SnapLayout.Maximize)?.map { it.toList() },
        )
    }
}
