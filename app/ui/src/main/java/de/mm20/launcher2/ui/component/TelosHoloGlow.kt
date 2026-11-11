package de.mm20.launcher2.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated Holographic Gradient Border Modifier.
 * Creates a smoothly rotating neon gradient glow around surfaces, cards, and input fields.
 */
fun Modifier.telosHoloGlow(
    shape: Shape,
    borderWidth: Dp = 1.5.dp,
    colors: List<Color> = listOf(
        Color(0xFF00F2FE), // Cyan
        Color(0xFFA855F7), // Purple
        Color(0xFFFF007F), // Neon Pink
        Color(0xFF00F2FE)  // Repeat Cyan for loop
    ),
    animationDurationMs: Int = 3000,
    backgroundColor: Color = Color(0xFF0D0E15).copy(alpha = 0.88f)
): Modifier = this.then(
    Modifier
        .clip(shape)
        .background(backgroundColor)
)

@Composable
fun Modifier.rememberTelosHoloBorder(
    shape: Shape,
    borderWidth: Dp = 1.5.dp,
    colors: List<Color> = listOf(
        Color(0xFF00F2FE),
        Color(0xFFA855F7),
        Color(0xFFFF007F),
        Color(0xFF00F2FE)
    ),
    animationDurationMs: Int = 3000
): Modifier {
    val transition = rememberInfiniteTransition(label = "holoGlow")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val brush = Brush.linearGradient(
        colors = colors,
        start = androidx.compose.ui.geometry.Offset(offset, 0f),
        end = androidx.compose.ui.geometry.Offset(offset + 500f, 500f)
    )

    return this
        .clip(shape)
        .border(borderWidth, brush, shape)
}
