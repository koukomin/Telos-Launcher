package com.telos.desktop.cursor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

enum class CursorPreset {
    SWEET_DARK_NEON,
    SWEET_PURPLE,
    BREEZE_DARK,
    BREEZE_LIGHT,
    KLASSY_DYNAMIC,
}

enum class CursorState {
    DEFAULT_ARROW,
    POINTER_HAND,
    TEXT_IBEAM,
    RESIZE_N,
    RESIZE_S,
    RESIZE_E,
    RESIZE_W,
    RESIZE_NE,
    RESIZE_NW,
    RESIZE_SE,
    RESIZE_SW,
    GRAB,
    LOADING_RING,
}

data class CursorPalette(
    val core: Color,
    val borderStart: Color,
    val borderEnd: Color,
    val glow: Color,
)

object TelosCursorEngine {
    fun palette(preset: CursorPreset): CursorPalette = when (preset) {
        CursorPreset.SWEET_DARK_NEON -> CursorPalette(
            core = Color(0xFF181623),
            borderStart = Color(0xFFFF007F),
            borderEnd = Color(0xFF00F0FF),
            glow = Color(0x6600F0FF),
        )
        CursorPreset.SWEET_PURPLE -> CursorPalette(
            core = Color(0xFF1A1028),
            borderStart = Color(0xFFB14EFF),
            borderEnd = Color(0xFFFF6AD5),
            glow = Color(0x66B14EFF),
        )
        CursorPreset.BREEZE_DARK -> CursorPalette(
            core = Color(0xFF232629),
            borderStart = Color(0xFF3DAEE9),
            borderEnd = Color(0xFF3DAEE9),
            glow = Color(0x443DAEE9),
        )
        CursorPreset.BREEZE_LIGHT -> CursorPalette(
            core = Color(0xFFFCFCFC),
            borderStart = Color(0xFF3DAEE9),
            borderEnd = Color(0xFF232629),
            glow = Color(0x22000000),
        )
        CursorPreset.KLASSY_DYNAMIC -> CursorPalette(
            core = Color(0xFF121212),
            borderStart = Color(0xFF61D6FF),
            borderEnd = Color(0xFFFF7A90),
            glow = Color(0x5561D6FF),
        )
    }

    fun DrawScope.drawCursor(state: CursorState, preset: CursorPreset, origin: Offset) {
        val pal = palette(preset)
        when (state) {
            CursorState.TEXT_IBEAM -> drawIbeam(origin, pal)
            CursorState.LOADING_RING -> drawRing(origin, pal)
            CursorState.POINTER_HAND -> drawHand(origin, pal)
            CursorState.GRAB -> drawGrab(origin, pal)
            CursorState.RESIZE_E, CursorState.RESIZE_W -> drawResizeH(origin, pal)
            CursorState.RESIZE_N, CursorState.RESIZE_S -> drawResizeV(origin, pal)
            else -> drawArrow(origin, pal)
        }
    }

    private fun DrawScope.drawArrow(o: Offset, pal: CursorPalette) {
        val path = Path().apply {
            moveTo(o.x, o.y)
            lineTo(o.x, o.y + 22f)
            lineTo(o.x + 6f, o.y + 16f)
            lineTo(o.x + 14f, o.y + 26f)
            lineTo(o.x + 17f, o.y + 24f)
            lineTo(o.x + 9f, o.y + 14f)
            lineTo(o.x + 16f, o.y + 14f)
            close()
        }
        drawPath(path, pal.glow, style = Stroke(width = 8f))
        drawPath(
            path,
            Brush.linearGradient(listOf(pal.borderStart, pal.borderEnd), o, o + Offset(16f, 26f)),
            style = Stroke(width = 2.4f),
        )
        drawPath(path, pal.core, style = Fill)
    }

    private fun DrawScope.drawIbeam(o: Offset, pal: CursorPalette) {
        drawRect(pal.glow, o + Offset(4f, -2f), Size(6f, 28f))
        drawRect(pal.core, o + Offset(6f, 0f), Size(2f, 24f))
        drawRect(pal.borderStart, o + Offset(2f, 0f), Size(10f, 2f))
        drawRect(pal.borderEnd, o + Offset(2f, 22f), Size(10f, 2f))
    }

    private fun DrawScope.drawRing(o: Offset, pal: CursorPalette) {
        drawCircle(pal.glow, 14f, o)
        drawCircle(
            Brush.sweepGradient(listOf(pal.borderStart, pal.borderEnd, pal.borderStart), o),
            10f,
            o,
            style = Stroke(width = 3f),
        )
    }

    private fun DrawScope.drawHand(o: Offset, pal: CursorPalette) {
        drawCircle(pal.glow, 12f, o + Offset(8f, 10f))
        drawCircle(pal.core, 8f, o + Offset(8f, 10f))
        drawCircle(pal.borderStart, 8f, o + Offset(8f, 10f), style = Stroke(2f))
    }

    private fun DrawScope.drawGrab(o: Offset, pal: CursorPalette) {
        drawCircle(pal.core, 9f, o + Offset(8f, 8f))
        drawCircle(pal.borderEnd, 9f, o + Offset(8f, 8f), style = Stroke(2.5f))
    }

    private fun DrawScope.drawResizeH(o: Offset, pal: CursorPalette) {
        drawLine(pal.borderStart, o + Offset(0f, 8f), o + Offset(20f, 8f), strokeWidth = 3f)
        drawLine(pal.core, o + Offset(0f, 8f), o + Offset(6f, 4f), strokeWidth = 2f)
        drawLine(pal.core, o + Offset(0f, 8f), o + Offset(6f, 12f), strokeWidth = 2f)
        drawLine(pal.core, o + Offset(20f, 8f), o + Offset(14f, 4f), strokeWidth = 2f)
        drawLine(pal.core, o + Offset(20f, 8f), o + Offset(14f, 12f), strokeWidth = 2f)
    }

    private fun DrawScope.drawResizeV(o: Offset, pal: CursorPalette) {
        drawLine(pal.borderEnd, o + Offset(8f, 0f), o + Offset(8f, 20f), strokeWidth = 3f)
        drawLine(pal.core, o + Offset(8f, 0f), o + Offset(4f, 6f), strokeWidth = 2f)
        drawLine(pal.core, o + Offset(8f, 0f), o + Offset(12f, 6f), strokeWidth = 2f)
        drawLine(pal.core, o + Offset(8f, 20f), o + Offset(4f, 14f), strokeWidth = 2f)
        drawLine(pal.core, o + Offset(8f, 20f), o + Offset(12f, 14f), strokeWidth = 2f)
    }
}
