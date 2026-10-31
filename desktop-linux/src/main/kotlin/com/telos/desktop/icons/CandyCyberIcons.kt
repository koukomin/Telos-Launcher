package com.telos.desktop.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CandyIcon {
    TelosLogo, AppGrid, Terminal, Monitor, Volume, Network, Battery, Power, Settings, Folder,
    Tray, Clip, Bell, Shot,
}

private val neon = Brush.linearGradient(listOf(Color(0xFFFF007F), Color(0xFF00F0FF)))
private val core = Color(0xFF181623)

@Composable
fun CandyCyberIcon(kind: CandyIcon, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).drawWithCache {
            val path = Path()
            onDrawBehind {
                path.reset()
                when (kind) {
                    CandyIcon.TelosLogo -> logo()
                    CandyIcon.AppGrid -> grid()
                    CandyIcon.Terminal -> terminal()
                    CandyIcon.Monitor -> monitor()
                    CandyIcon.Volume -> volume()
                    CandyIcon.Network -> network()
                    CandyIcon.Battery -> battery()
                    CandyIcon.Power -> power()
                    CandyIcon.Settings -> settings()
                    CandyIcon.Folder -> folder()
                    CandyIcon.Tray -> tray()
                    CandyIcon.Clip -> clip()
                    CandyIcon.Bell -> bell()
                    CandyIcon.Shot -> shot()
                }
            }
        },
    )
}

private fun DrawScope.logo() {
    val path = Path().apply {
        moveTo(size.width * 0.18f, size.height * 0.78f)
        quadraticBezierTo(size.width * 0.12f, size.height * 0.2f, size.width * 0.5f, size.height * 0.12f)
        quadraticBezierTo(size.width * 0.88f, size.height * 0.2f, size.width * 0.82f, size.height * 0.78f)
        lineTo(size.width * 0.5f, size.height * 0.92f)
        close()
    }
    drawPath(path, Color(0x6600F0FF), style = Stroke(8f))
    drawPath(path, neon, style = Fill)
    drawCircle(core, size.minDimension * 0.12f, Offset(size.width * 0.5f, size.height * 0.48f))
    drawCircle(Color(0xFF00F0FF), size.minDimension * 0.12f, Offset(size.width * 0.5f, size.height * 0.48f), style = Stroke(2.2f))
}

private fun DrawScope.grid() {
    val gap = size.minDimension * 0.12f
    val cell = (size.minDimension - gap * 4) / 3f
    for (r in 0..2) for (c in 0..2) {
        val x = gap + c * (cell + gap)
        val y = gap + r * (cell + gap)
        drawRoundRect(neon, Offset(x, y), Size(cell, cell), CornerRadius(cell * 0.28f, cell * 0.28f))
    }
}

private fun DrawScope.terminal() {
    drawRoundRect(core, Offset(2f, 4f), Size(size.width - 4f, size.height - 8f), CornerRadius(6f, 6f))
    drawRoundRect(neon, Offset(2f, 4f), Size(size.width - 4f, size.height - 8f), CornerRadius(6f, 6f), style = Stroke(2f))
    drawLine(Color(0xFF00F0FF), Offset(8f, size.height * 0.45f), Offset(size.width * 0.38f, size.height * 0.62f), 2.4f, StrokeCap.Round)
    drawLine(Color(0xFFFF007F), Offset(size.width * 0.42f, size.height * 0.68f), Offset(size.width * 0.72f, size.height * 0.68f), 2.4f, StrokeCap.Round)
}

private fun DrawScope.monitor() {
    drawCircle(neon, size.minDimension * 0.42f, center, style = Stroke(3f))
    drawLine(Color(0xFF00F0FF), Offset(center.x, size.height * 0.22f), Offset(center.x, size.height * 0.55f), 3f)
    drawLine(Color(0xFFFF007F), Offset(size.width * 0.32f, size.height * 0.62f), Offset(size.width * 0.68f, size.height * 0.62f), 3f)
}

private fun DrawScope.volume() {
    val p = Path().apply {
        moveTo(size.width * 0.18f, size.height * 0.4f)
        lineTo(size.width * 0.38f, size.height * 0.4f)
        lineTo(size.width * 0.58f, size.height * 0.22f)
        lineTo(size.width * 0.58f, size.height * 0.78f)
        lineTo(size.width * 0.38f, size.height * 0.6f)
        lineTo(size.width * 0.18f, size.height * 0.6f)
        close()
    }
    drawPath(p, neon)
    drawArc(Color(0xFF00F0FF), -40f, 80f, false, topLeft = Offset(size.width * 0.52f, size.height * 0.28f), size = Size(size.width * 0.38f, size.height * 0.44f), style = Stroke(2.2f))
}

private fun DrawScope.network() {
    drawCircle(neon, 4f, Offset(size.width * 0.5f, size.height * 0.72f))
    drawArc(Color(0xFF00F0FF), 210f, 120f, false, topLeft = Offset(size.width * 0.22f, size.height * 0.28f), size = Size(size.width * 0.56f, size.height * 0.56f), style = Stroke(2.2f))
    drawArc(Color(0xFFFF007F), 210f, 120f, false, topLeft = Offset(size.width * 0.08f, size.height * 0.1f), size = Size(size.width * 0.84f, size.height * 0.84f), style = Stroke(2.2f))
}

private fun DrawScope.battery() {
    drawRoundRect(neon, Offset(4f, size.height * 0.28f), Size(size.width * 0.78f, size.height * 0.44f), CornerRadius(4f, 4f), style = Stroke(2f))
    drawRect(Color(0xFF00F0FF), Offset(size.width * 0.84f, size.height * 0.4f), Size(size.width * 0.08f, size.height * 0.2f))
    drawRect(Color(0xFFFF007F), Offset(8f, size.height * 0.34f), Size(size.width * 0.5f, size.height * 0.32f))
}

private fun DrawScope.power() {
    drawArc(neon, 40f, 280f, false, style = Stroke(3.2f), topLeft = Offset(4f, 4f), size = Size(size.width - 8f, size.height - 8f))
    drawLine(Color(0xFF00F0FF), Offset(center.x, 3f), Offset(center.x, size.height * 0.48f), 3.2f, StrokeCap.Round)
}

private fun DrawScope.settings() {
    drawCircle(neon, size.minDimension * 0.18f, center, style = Stroke(3f))
    for (i in 0..5) {
        val a = Math.toRadians(i * 60.0)
        val x = center.x + kotlin.math.cos(a).toFloat() * size.minDimension * 0.38f
        val y = center.y + kotlin.math.sin(a).toFloat() * size.minDimension * 0.38f
        drawCircle(Color(0xFFFF007F), 3f, Offset(x, y))
    }
}

private fun DrawScope.folder() {
    val p = Path().apply {
        moveTo(4f, size.height * 0.32f)
        lineTo(size.width * 0.38f, size.height * 0.32f)
        lineTo(size.width * 0.48f, size.height * 0.22f)
        lineTo(size.width - 4f, size.height * 0.22f)
        lineTo(size.width - 4f, size.height * 0.82f)
        lineTo(4f, size.height * 0.82f)
        close()
    }
    drawPath(p, neon)
}

private fun DrawScope.tray() {
    drawRoundRect(neon, Offset(4f, size.height * 0.58f), Size(size.width - 8f, size.height * 0.28f), CornerRadius(3f, 3f), style = Stroke(2f))
    drawCircle(Color(0xFF00F0FF), 3f, Offset(size.width * 0.3f, size.height * 0.72f))
    drawCircle(Color(0xFFFF007F), 3f, Offset(size.width * 0.5f, size.height * 0.72f))
    drawCircle(Color(0xFF00F0FF), 3f, Offset(size.width * 0.7f, size.height * 0.72f))
}

private fun DrawScope.clip() {
    drawRoundRect(neon, Offset(size.width * 0.28f, size.height * 0.22f), Size(size.width * 0.44f, size.height * 0.62f), CornerRadius(4f, 4f), style = Stroke(2.2f))
    drawRoundRect(Color(0xFF00F0FF), Offset(size.width * 0.36f, size.height * 0.12f), Size(size.width * 0.28f, size.height * 0.18f), CornerRadius(3f, 3f), style = Stroke(2f))
}

private fun DrawScope.bell() {
    drawArc(neon, 200f, 140f, false, topLeft = Offset(6f, 6f), size = Size(size.width - 12f, size.height - 8f), style = Stroke(2.4f))
    drawLine(Color(0xFFFF007F), Offset(size.width * 0.32f, size.height * 0.78f), Offset(size.width * 0.68f, size.height * 0.78f), 2.4f, StrokeCap.Round)
}

private fun DrawScope.shot() {
    drawCircle(neon, size.minDimension * 0.22f, Offset(center.x, center.y + 2f), style = Stroke(2.4f))
    drawRoundRect(Color(0xFF00F0FF), Offset(6f, size.height * 0.28f), Size(size.width - 12f, size.height * 0.5f), CornerRadius(4f, 4f), style = Stroke(2f))
    drawCircle(Color(0xFFFF007F), 3f, Offset(size.width * 0.72f, size.height * 0.38f))
}
