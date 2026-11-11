package de.mm20.launcher2.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tactical Scanline CRT Overlay Modifier.
 * Adds fine horizontal scanlines to cards, radar banners, and HUD panels.
 */
fun Modifier.telosScanlines(
    scanlineColor: Color = Color(0xFF00F2FE),
    lineSpacing: Dp = 4.dp,
    alpha: Float = 0.08f
): Modifier = this.drawWithContent {
    drawContent()
    val spacingPx = lineSpacing.toPx()
    var y = 0f
    val lineColor = scanlineColor.copy(alpha = alpha)
    while (y < size.height) {
        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += spacingPx
    }
}
