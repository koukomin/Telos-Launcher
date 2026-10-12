package de.mm20.launcher2.ui.media.music

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

/** Click / long click with a spring press scale (0.97). No animation when [reduce]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.pressScale(reduce: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reduce) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "pressScale",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick)
}

/** A rounded skeleton box with a moving highlight (static when [reduce]) */
@Composable
internal fun ShimmerBox(modifier: Modifier, reduce: Boolean, shape: Shape = RoundedCornerShape(20.dp)) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    val shift = if (reduce) 0f else {
        val t = rememberInfiniteTransition(label = "shimmer")
        val v by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "shimmerShift")
        v
    }
    Box(
        modifier.clip(shape).drawBehind {
            val w = size.width.coerceAtLeast(1f)
            val x = shift * w
            drawRect(Brush.linearGradient(listOf(base, light, base), Offset(x, 0f), Offset(x + w, 0f)))
        },
    )
}

/** Skeleton of the Music home page while the library loads */
@Composable
internal fun MusicHomeSkeleton(reduce: Boolean) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { ShimmerBox(Modifier.size(width = 72.dp, height = 36.dp), reduce, RoundedCornerShape(18.dp)) }
        }
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { ShimmerBox(Modifier.weight(1f).aspectRatio(1f), reduce) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { ShimmerBox(Modifier.size(148.dp), reduce) }
        }
    }
}

/** Empty state: big tinted icon, a title and an optional line */
@Composable
internal fun MusicEmptyState(title: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.music_note_48px), contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Three bouncing bars; frozen when [animate] is false */
@Composable
internal fun EqualizerGlyph(color: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val a = if (animate) t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "eqA").value else 0.7f
    val b = if (animate) t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(540, easing = LinearEasing), RepeatMode.Reverse), label = "eqB").value else 0.4f
    val c = if (animate) t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(360, easing = LinearEasing), RepeatMode.Reverse), label = "eqC").value else 1f
    Box(
        modifier.size(18.dp).drawBehind {
            val barW = size.width / 5f
            listOf(a, b, c).forEachIndexed { i, h ->
                val bh = size.height * h
                drawRoundRect(color, Offset(i * 2 * barW, size.height - bh), Size(barW, bh), CornerRadius(barW / 2))
            }
        },
    )
}
