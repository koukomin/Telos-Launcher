package de.mm20.launcher2.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CyanNeon = Color(0xFF00F2FE)
private val PinkNeon = Color(0xFFFF007F)
private val PurpleNeon = Color(0xFF8A2BE2)
private val DarkBg = Color(0xFF0D0E15)

@Composable
fun CyberRadialGauge(
    title: String,
    value: Float, // 0.0f .. 1.0f
    valueText: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    primaryColor: Color = CyanNeon,
    accentColor: Color = PinkNeon,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "gauge_progress",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkBg.copy(alpha = 0.85f))
            .border(1.dp, Brush.horizontalGradient(listOf(primaryColor.copy(alpha = 0.5f), accentColor.copy(alpha = 0.5f))), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            letterSpacing = 1.5.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(12.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Canvas(modifier = Modifier.size(size)) {
                val strokeWidth = 10.dp.toPx()
                val arcSize = Size(size.toPx() - strokeWidth, size.toPx() - strokeWidth)
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                // Background track arc (240 degrees)
                drawArc(
                    color = Color.White.copy(alpha = 0.08f),
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Animated Neon progress arc
                val sweepAngle = 240f * animatedProgress
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to primaryColor,
                        0.5f to accentColor,
                        1.0f to primaryColor,
                    ),
                    startAngle = 150f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = valueText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun CyberWaveformChart(
    downloadKb: Long,
    uploadKb: Long,
    history: List<Pair<Long, Long>>, // (dl, ul) history
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkBg.copy(alpha = 0.85f))
            .border(1.dp, Brush.horizontalGradient(listOf(CyanNeon.copy(alpha = 0.5f), PurpleNeon.copy(alpha = 0.5f))), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NETWORK TRAFFIC WAVEFORM",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CyanNeon,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "DL: ${downloadKb} KB/s  ·  UL: ${uploadKb} KB/s",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            val width = size.width
            val height = size.height

            // Grid lines
            for (i in 1..3) {
                val y = height * (i / 4f)
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            if (history.size >= 2) {
                val maxVal = history.flatMap { listOf(it.first, it.second) }.maxOrNull()?.coerceAtLeast(100L) ?: 100L
                val stepX = width / (history.size - 1)

                val dlPath = androidx.compose.ui.graphics.Path()
                val ulPath = androidx.compose.ui.graphics.Path()

                history.forEachIndexed { index, (dl, ul) ->
                    val x = index * stepX
                    val yDl = height - (dl.toFloat() / maxVal * height).coerceIn(2f, height)
                    val yUl = height - (ul.toFloat() / maxVal * height).coerceIn(2f, height)

                    if (index == 0) {
                        dlPath.moveTo(x, yDl)
                        ulPath.moveTo(x, yUl)
                    } else {
                        dlPath.lineTo(x, yDl)
                        ulPath.lineTo(x, yUl)
                    }
                }

                drawPath(
                    path = dlPath,
                    color = CyanNeon,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
                drawPath(
                    path = ulPath,
                    color = PinkNeon,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}
