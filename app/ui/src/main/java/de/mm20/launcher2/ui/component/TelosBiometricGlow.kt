package de.mm20.launcher2.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.base.R as BaseR

/**
 * Cyber Biometric Scanner with Concentric Glowing Energy Rings.
 */
@Composable
fun TelosBiometricScanner(
    isUnlocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerSize: Dp = 80.dp,
    accentColor: Color = Color(0xFF10B981)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bioScanner")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(containerSize)
            .clickable { onClick() }
    ) {
        Canvas(modifier = Modifier.size(containerSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f - 4.dp.toPx()

            // Outer pulse ring
            drawCircle(
                color = accentColor.copy(alpha = 0.25f * (1.2f - pulseRadius)),
                radius = maxR * pulseRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner solid ring
            drawCircle(
                color = accentColor.copy(alpha = 0.4f),
                radius = maxR * 0.75f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        Icon(
            painter = painterResource(if (isUnlocked) BaseR.drawable.lock_open_20px else BaseR.drawable.lock_24px),
            contentDescription = "Biometric Lock",
            tint = if (isUnlocked) accentColor else Color(0xFFF59E0B),
            modifier = Modifier.size(32.dp)
        )
    }
}
