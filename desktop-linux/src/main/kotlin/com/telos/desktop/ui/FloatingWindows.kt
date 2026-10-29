package com.telos.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.telos.desktop.wm.ManagedWindow
import com.telos.desktop.wm.TelosWindowManager
import com.telos.desktop.wm.WindowButtonStyle
import kotlin.math.roundToInt

@Composable
fun FloatingWindowLayer(
    wm: TelosWindowManager,
    content: @Composable (ManagedWindow) -> Unit,
) {
    val density = LocalDensity.current
    wm.windows.forEach { win ->
        val theme = wm.decoration
        Box(
            modifier = Modifier
                .offset { IntOffset(win.x.roundToInt(), win.y.roundToInt()) }
                .size(with(density) { win.width.toDp() }, with(density) { (if (win.shaded) theme.titleBarHeightPx else win.height).toDp() })
                .alpha(win.opacity)
                .pointerInput(win.id) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        wm.drag(win.id, drag, androidx.compose.ui.unit.IntSize(3000, 2000))
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = theme.cornerRadiusPx
                val active = wm.focusedId == win.id
                if (theme.glassTitleBar) {
                    drawRoundRect(theme.titleBarTint, size = Size(size.width, theme.titleBarHeightPx), cornerRadius = CornerRadius(r, r))
                }
                drawRoundRect(
                    color = if (active) theme.activeOutline else theme.inactiveOutline,
                    size = size,
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = if (active) 3f else 1f),
                )
                drawWindowButtons(theme.buttonStyle, theme.buttonsOnLeft, size.width, theme.titleBarHeightPx, theme.accent)
            }
            Box(Modifier.padding(top = 36.dp).fillMaxSize().background(TelosColors.surface.copy(alpha = 0.96f))) {
                if (!win.shaded) content(win)
            }
        }
    }
}

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
