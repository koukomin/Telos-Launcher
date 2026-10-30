package com.telos.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.telos.desktop.wm.ManagedWindow
import com.telos.desktop.wm.ResizeEdge
import com.telos.desktop.wm.TelosWindowManager
import com.telos.desktop.wm.WindowButtonStyle
import kotlin.math.roundToInt

// === TELOS_PENDING_REVIEW_START: telos_window_drag_and_ui_fix ===
private val lastTitleClickMs = mutableMapOf<Long, Long>()

@Composable
fun FloatingWindowLayer(
    wm: TelosWindowManager,
    content: @Composable (ManagedWindow) -> Unit,
) {
    val density = LocalDensity.current
    wm.visibleWindows().sortedBy { it.z }.forEach { win ->
        key(win.id) {
        val theme = wm.decoration
        val barH = theme.titleBarHeightPx
        val bodyH = if (win.shaded) barH else win.height
        Box(
            modifier = Modifier
                .zIndex(10f + win.z)
                .offset { IntOffset(win.x.roundToInt(), win.y.roundToInt()) }
                .size(with(density) { win.width.toDp() }, with(density) { bodyH.toDp() })
                .shadow(if (wm.focusedId == win.id) 18.dp else 6.dp)
                .alpha(win.opacity),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = theme.cornerRadiusPx
                val active = wm.focusedId == win.id
                drawRoundRect(Color(0x14000000), topLeft = Offset(4f, 6f), size = Size(size.width, size.height), cornerRadius = CornerRadius(r, r))
                drawRoundRect(
                    color = if (active) theme.activeOutline else theme.inactiveOutline,
                    size = size,
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = if (active) 3f else 1f),
                )
            }
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { barH.toDp() })
                        .pointerInput(win.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                wm.focus(win.id)
                                var dragged = false
                                var last = down.position
                                val origin = down.position
                                val slop = viewConfiguration.touchSlop
                                drag(down.id) { change ->
                                    val delta = change.position - last
                                    last = change.position
                                    if ((change.position - origin).getDistance() > slop || dragged) {
                                        dragged = true
                                        change.consume()
                                        wm.drag(
                                            win.id,
                                            delta,
                                            IntSize(wm.workArea.screenWidth.toInt(), wm.workArea.screenHeight.toInt()),
                                        )
                                    }
                                }
                                if (dragged) {
                                    wm.endDragSnap(win.id)
                                } else {
                                    val now = System.currentTimeMillis()
                                    val prev = lastTitleClickMs[win.id] ?: 0L
                                    if (now - prev < 350L) {
                                        wm.toggleShade(win.id)
                                        lastTitleClickMs.remove(win.id)
                                    } else {
                                        lastTitleClickMs[win.id] = now
                                    }
                                }
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val r = theme.cornerRadiusPx
                        if (theme.glassTitleBar) {
                            drawRoundRect(theme.titleBarTint, size = Size(size.width, size.height), cornerRadius = CornerRadius(r, r))
                        }
                        drawWindowButtons(theme.buttonStyle, theme.buttonsOnLeft, size.width, size.height, theme.accent)
                    }
                    val start = if (theme.buttonsOnLeft) 8f else win.width - 84f
                    listOf(0 to { wm.close(win.id) }, 1 to { wm.toggleMaximize(win.id) }, 2 to { wm.minimize(win.id) }).forEach { (i, action) ->
                        Box(
                            Modifier
                                .offset { IntOffset((start + i * 22f).roundToInt(), 4) }
                                .size(18.dp)
                                .zIndex(4f)
                                .clickable { action(); wm.focus(win.id) },
                        )
                    }
                }
                if (!win.shaded) {
                    Box(Modifier.fillMaxSize().background(TelosColors.surface.copy(alpha = 0.96f))) {
                        content(win)
                    }
                }
            }
            edgeHandle(wm, win, ResizeEdge.N, Offset(12f, 0f), Size(win.width - 24f, 8f), density)
            edgeHandle(wm, win, ResizeEdge.S, Offset(12f, bodyH - 8f), Size(win.width - 24f, 8f), density)
            edgeHandle(wm, win, ResizeEdge.W, Offset(0f, 12f), Size(8f, bodyH - 24f), density)
            edgeHandle(wm, win, ResizeEdge.E, Offset(win.width - 8f, 12f), Size(8f, bodyH - 24f), density)
            edgeHandle(wm, win, ResizeEdge.NW, Offset(0f, 0f), Size(14f, 14f), density)
            edgeHandle(wm, win, ResizeEdge.NE, Offset(win.width - 14f, 0f), Size(14f, 14f), density)
            edgeHandle(wm, win, ResizeEdge.SW, Offset(0f, bodyH - 14f), Size(14f, 14f), density)
            edgeHandle(wm, win, ResizeEdge.SE, Offset(win.width - 14f, bodyH - 14f), Size(14f, 14f), density)
        }
        }
    }
}

@Composable
private fun edgeHandle(
    wm: TelosWindowManager,
    win: ManagedWindow,
    edge: ResizeEdge,
    origin: Offset,
    size: Size,
    density: androidx.compose.ui.unit.Density,
) {
    Box(
        Modifier
            .zIndex(5f)
            .offset { IntOffset(origin.x.roundToInt(), origin.y.roundToInt()) }
            .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
            .pointerInput(win.id, edge) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    wm.focus(win.id)
                    wm.resize(win.id, edge, dragAmount)
                }
            },
    )
}
// === TELOS_PENDING_REVIEW_END: telos_window_drag_and_ui_fix ===

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWindowButtons(
    style: WindowButtonStyle,
    left: Boolean,
    width: Float,
    height: Float,
    accent: Color,
) {
    val ys = height / 2f
    val start = if (left) 18f else width - 78f
    val colors = when (style) {
        WindowButtonStyle.SIERRA_MACOS -> listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840))
        WindowButtonStyle.SIERRA_BREEZE_BLEND -> listOf(Color(0xFFFF5F57), Color(0xFF3DAEE9), Color(0xFF28C840))
        WindowButtonStyle.KLASSY_ACCENT_FILLED -> listOf(accent, Color(0xFF00F0FF), Color(0xFF8888AA))
        else -> listOf(Color.Transparent, Color.Transparent, Color.Transparent)
    }
    colors.forEachIndexed { i, c ->
        val x = start + i * 22f
        when (style) {
            WindowButtonStyle.KLASSY_OUTLINE -> drawCircle(Color.White, 6f, Offset(x, ys), style = Stroke(1.5f))
            WindowButtonStyle.KLASSY_ICON_ONLY -> {
                drawLine(Color.White, Offset(x - 4f, ys), Offset(x + 4f, ys), strokeWidth = 1.5f)
            }
            else -> drawCircle(c, 6f, Offset(x, ys))
        }
    }
}
