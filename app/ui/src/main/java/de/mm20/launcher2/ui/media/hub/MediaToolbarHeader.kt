package de.mm20.launcher2.ui.media.hub

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

/**
 * Animated Laser-stroked linear icon header bar for Telos Media.
 * Replaces large text header bars with a sleek glowing laser icon and essential action buttons.
 */
@Composable
internal fun MediaToolbarHeader(
    title: String,
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    sourceMenu: @Composable () -> Unit = {},
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.hc_back))
                }

                Spacer(Modifier.width(4.dp))

                // Sleek animated glowing laser stroke icon
                Box {
                    LaserAnimatedMediaIcon(
                        onClick = onOpenSources,
                        modifier = Modifier.testTag("media-source-selector")
                    )
                    sourceMenu()
                }

                Spacer(Modifier.weight(1f))

                // Action buttons for current tab (Search, Add, Settings)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

@Composable
fun LaserAnimatedMediaIcon(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_anim")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laserProgress"
    )

    val laserBrush = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF00F2FE), // Laser Cyan
            Color(0xFF4FACFE), // Electric Blue
            Color(0xFF818CF8), // Neon Purple
            Color(0xFFFF007F), // Glowing Pink
            Color(0xFF00F2FE)  // Laser Cyan
        ),
        startX = laserProgress * 600f,
        endX = (laserProgress + 1f) * 600f
    )

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 36.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            val strokeWidthPx = 2.dp.toPx()
            // Outer laser rounded stroke
            drawRoundRect(
                brush = laserBrush,
                size = size,
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = strokeWidthPx)
            )
            // Inner linear waveform icon
            val path = Path().apply {
                val w = size.width
                val h = size.height
                moveTo(w * 0.22f, h * 0.60f)
                lineTo(w * 0.40f, h * 0.35f)
                lineTo(w * 0.58f, h * 0.68f)
                lineTo(w * 0.78f, h * 0.30f)
            }
            drawPath(
                path = path,
                brush = laserBrush,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}


