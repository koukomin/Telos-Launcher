package de.mm20.launcher2.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.media.PlaybackSpectrum

private val CyanNeon = Color(0xFF00F2FE)
private val PinkNeon = Color(0xFFFF007F)

@Composable
fun AudioSpectrumVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = PlaybackSpectrum.BAR_COUNT,
) {
    DisposableEffect(Unit) {
        PlaybackSpectrum.addListener()
        onDispose { PlaybackSpectrum.removeListener() }
    }
    val live by PlaybackSpectrum.bars.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D0E15).copy(alpha = 0.85f))
            .border(1.dp, Brush.horizontalGradient(listOf(CyanNeon.copy(alpha = 0.4f), PinkNeon.copy(alpha = 0.4f))), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            val totalWidth = size.width
            val height = size.height
            val gap = 4.dp.toPx()
            val barWidth = (totalWidth - (gap * (barCount - 1))) / barCount
            val n = minOf(barCount, live.size)

            for (i in 0 until barCount) {
                val sample = if (i < n) live[i] else 0f
                val factor = if (isPlaying) sample.coerceIn(0.06f, 1f) else 0.08f
                val barHeight = height * factor
                val x = i * (barWidth + gap)
                val y = height - barHeight
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(PinkNeon, CyanNeon)),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}
