package de.mm20.launcher2.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * 12-Band Sci-Fi Audio Spectrum Visualizer Component.
 */
@Composable
fun TelosAudioSpectrum(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    bandCount: Int = 12,
    accentColor: Color = Color(0xFFA855F7),
    height: Dp = 28.dp
) {
    val transition = rememberInfiniteTransition(label = "spectrum")
    val animVal by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "anim"
    )

    Canvas(modifier = modifier.height(height)) {
        val width = size.width
        val barWidth = (width / bandCount) * 0.65f
        val gap = (width - (barWidth * bandCount)) / (bandCount + 1)
        val maxHeight = size.height

        for (i in 0 until bandCount) {
            val factor = if (isPlaying) {
                (0.2f + 0.8f * kotlin.math.abs(sin(animVal + i * 0.55f))).coerceIn(0.15f, 1.0f)
            } else {
                0.15f
            }

            val barH = maxHeight * factor
            val x = gap + i * (barWidth + gap)
            val y = maxHeight - barH

            // Bar background
            drawRoundRect(
                color = accentColor.copy(alpha = 0.85f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Top peak dot
            if (isPlaying) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = (barWidth / 2f).coerceAtMost(3.dp.toPx()),
                    center = Offset(x + barWidth / 2f, (y - 3.dp.toPx()).coerceAtLeast(0f))
                )
            }
        }
    }
}
