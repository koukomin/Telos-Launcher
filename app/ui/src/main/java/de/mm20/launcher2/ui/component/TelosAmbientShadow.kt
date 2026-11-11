package de.mm20.launcher2.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom Ambient Glow Shadow Modifier.
 * Casts a soft colored halo/glow behind cards and panels matching their neon accent color.
 */
fun Modifier.telosAmbientGlow(
    glowColor: Color = Color(0xFF00F2FE),
    borderRadius: Dp = 18.dp,
    glowRadius: Dp = 12.dp,
    alpha: Float = 0.35f
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.TRANSPARENT
            setShadowLayer(
                glowRadius.toPx(),
                0f,
                0f,
                android.graphics.Color.argb(
                    (alpha * 255).toInt(),
                    (glowColor.red * 255).toInt(),
                    (glowColor.green * 255).toInt(),
                    (glowColor.blue * 255).toInt()
                )
            )
        }
        val rPx = borderRadius.toPx()
        canvas.nativeCanvas.drawRoundRect(
            0f,
            0f,
            size.width,
            size.height,
            rPx,
            rPx,
            paint
        )
    }
}
