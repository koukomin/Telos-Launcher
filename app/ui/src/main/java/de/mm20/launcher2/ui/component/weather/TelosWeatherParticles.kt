package de.mm20.launcher2.ui.component.weather

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Live Weather Micro-Particle Effect Component.
 * Renders rain drops, snow dots, or sun rays on Canvas over weather cards.
 */
@Composable
fun TelosWeatherParticles(
    condition: String,
    modifier: Modifier = Modifier,
    particleColor: Color = Color(0xFF00F2FE)
) {
    val isRain = condition.contains("rain", ignoreCase = true) || condition.contains("βροχή", ignoreCase = true)
    val isSnow = condition.contains("snow", ignoreCase = true) || condition.contains("χιόνι", ignoreCase = true)

    val transition = rememberInfiniteTransition(label = "particles")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isRain) 1200 else 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    val particles = remember(condition) {
        List(24) {
            Offset(
                x = Random.nextFloat(),
                y = Random.nextFloat()
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        particles.forEach { p ->
            val curY = (p.y + progress) % 1.0f
            val posX = p.x * width
            val posY = curY * height

            if (isRain) {
                // Rain drop streaks
                drawLine(
                    color = particleColor.copy(alpha = 0.45f),
                    start = Offset(posX, posY),
                    end = Offset(posX - 4.dp.toPx(), posY + 14.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            } else if (isSnow) {
                // Drifting snow dots
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f),
                    radius = (2.dp.toPx() + p.x * 3.dp.toPx()),
                    center = Offset(posX, posY)
                )
            } else {
                // Sun light particles
                drawCircle(
                    color = Color(0xFFFFB800).copy(alpha = 0.25f),
                    radius = (3.dp.toPx() + p.x * 4.dp.toPx()),
                    center = Offset(posX, posY)
                )
            }
        }
    }
}
