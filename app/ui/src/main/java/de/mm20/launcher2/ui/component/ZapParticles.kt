package de.mm20.launcher2.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Stable
private data class Particle(
    val angle: Float,
    val speed: Float,
    val radius: Float,
    val color: Color,
)

/**
 * High-performance, zero-allocation Compose Canvas particle effect for Lightning Zaps (NIP-57).
 * Uses drawWithCache to prevent object allocations during the animation loop.
 */
@Composable
fun ZapParticleExplosion(
    trigger: Boolean,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    particleCount: Int = 18,
) {
    if (!trigger) return

    val progress = remember { Animatable(0f) }
    val particles = remember(trigger) {
        val colors = listOf(
            Color(0xFFFFD700), // Gold
            Color(0xFFFF9900), // Amber
            Color(0xFFFFEA00), // Electric Yellow
            Color(0xFF00E5FF), // Cyan Accent
        )
        List(particleCount) {
            Particle(
                angle = Random.nextFloat() * 2f * Math.PI.toFloat(),
                speed = Random.nextFloat() * 180f + 80f,
                radius = Random.nextFloat() * 6f + 4f,
                color = colors[it % colors.size],
            )
        }
    }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        )
        onFinished()
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                onDrawWithContent {
                    val p = progress.value
                    if (p <= 0f || p >= 1f) return@onDrawWithContent

                    val alpha = (1f - p).coerceIn(0f, 1f)
                    for (i in particles.indices) {
                        val particle = particles[i]
                        val dist = particle.speed * p
                        val x = center.x + cos(particle.angle) * dist
                        val y = center.y + sin(particle.angle) * dist
                        val currentRadius = particle.radius * (1f - p * 0.5f)

                        drawCircle(
                            color = particle.color.copy(alpha = alpha),
                            radius = currentRadius,
                            center = Offset(x, y),
                        )
                    }
                }
            }
    ) {
        // Draw handled in drawWithCache
    }
}
