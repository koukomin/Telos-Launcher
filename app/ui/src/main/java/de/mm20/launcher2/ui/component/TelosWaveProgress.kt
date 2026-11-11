package de.mm20.launcher2.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Ultra-smooth Wavy / Sine-wave Progress Bar inspired by Morphe & modern Cyber UI.
 * Replaces traditional flat linear progress indicators with an animated fluid waveform.
 */
@Composable
fun TelosWaveProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 3.5.dp,
    waveAmplitude: Dp = 4.5.dp,
    waveFrequency: Float = 0.08f,
    isAnimated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "telosWaveAnim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val currentPhase = if (isAnimated) phase else 0f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
    ) {
        val width = size.width
        val activeWidth = (width * progress.coerceIn(0f, 1f))
        val centerY = size.height / 2f
        val ampPx = waveAmplitude.toPx()
        val strokePx = strokeWidth.toPx()

        if (activeWidth > 0f) {
            val wavePath = Path().apply {
                moveTo(0f, centerY + ampPx * sin(currentPhase))
                var x = 0f
                val step = 2f
                while (x <= activeWidth) {
                    val y = centerY + ampPx * sin(x * waveFrequency + currentPhase)
                    lineTo(x, y)
                    x += step
                }
            }

            // Draw glowing active wave stroke
            drawPath(
                path = wavePath,
                color = color,
                style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Draw inactive track line for remaining progress
        if (activeWidth < width) {
            drawLine(
                color = color.copy(alpha = 0.22f),
                start = Offset(activeWidth.coerceAtLeast(0f), centerY),
                end = Offset(width, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }
    }
}
