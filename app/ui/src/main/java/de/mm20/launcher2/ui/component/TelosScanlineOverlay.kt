package de.mm20.launcher2.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ultra High-Performance Cached Tactical Scanline CRT Overlay Modifier.
 * Caches scanline positions in hardware scope to ensure zero draw-pass allocations.
 */
fun Modifier.telosScanlines(
    scanlineColor: Color = Color(0xFF00F2FE),
    lineSpacing: Dp = 4.dp,
    alpha: Float = 0.08f
): Modifier = this.drawWithCache {
    val spacingPx = lineSpacing.toPx()
    val lineColor = scanlineColor.copy(alpha = alpha)
    val height = size.height
    val width = size.width

    onDrawWithContent {
        drawContent()
        var y = 0f
        while (y < height) {
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            y += spacingPx
        }
    }
}
