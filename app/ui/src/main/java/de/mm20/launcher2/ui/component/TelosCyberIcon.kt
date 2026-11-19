package de.mm20.launcher2.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Universal Cyber-Tech Icon Composable with dynamic neon halo glow, active scale micro-animations,
 * dot-matrix telemetry indicators, and theme awareness for Telos Launcher.
 */
@Composable
fun TelosCyberIcon(
    painter: Painter,
    contentDescription: String?,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = TelosCyber.NeonCyan,
    iconSize: Dp = 24.dp,
    showIndicator: Boolean = true,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1.0f,
        animationSpec = tween<Float>(durationMillis = 200),
        label = "cyberIconScale",
    )
    val animatedAccent by animateColorAsState(
        targetValue = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "cyberIconColor",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(iconSize + 10.dp),
        ) {
            // Outer Neon Halo Glow
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(iconSize + 8.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.35f),
                                    accentColor.copy(alpha = 0.10f),
                                    Color.Transparent,
                                )
                            )
                        )
                )
            }

            // High-Tech Icon Display
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = animatedAccent,
                modifier = Modifier
                    .size(iconSize)
                    .scale(scale),
            )
        }

        if (showIndicator && isSelected) {
            Spacer(Modifier.height(1.dp))
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(accentColor)
            )
        }
    }
}

@Composable
fun TelosCyberIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = TelosCyber.NeonCyan,
    iconSize: Dp = 24.dp,
    showIndicator: Boolean = true,
) {
    TelosCyberIcon(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        isSelected = isSelected,
        modifier = modifier,
        accentColor = accentColor,
        iconSize = iconSize,
        showIndicator = showIndicator,
    )
}
