package de.mm20.launcher2.ui.network

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.network.impl.TelosNetworkSpeedometer

@Composable
fun LiveTrafficGraph(modifier: Modifier = Modifier) {
    val speedState by TelosNetworkSpeedometer.speedState.collectAsState()

    LaunchedEffect(Unit) {
        TelosNetworkSpeedometer.startMonitoring()
    }

    val maxPoints = 50
    val rxHistory = remember { mutableStateListOf<Float>() }
    val txHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(speedState) {
        rxHistory.add(speedState.downloadSpeedBytesPerSec.toFloat())
        txHistory.add(speedState.uploadSpeedBytesPerSec.toFloat())
        if (rxHistory.size > maxPoints) rxHistory.removeAt(0)
        if (txHistory.size > maxPoints) txHistory.removeAt(0)
    }

    Canvas(modifier = modifier.fillMaxWidth().height(100.dp)) {
        val width = size.width
        val height = size.height

        val maxVal = maxOf(
            1f,
            rxHistory.maxOrNull() ?: 1f,
            txHistory.maxOrNull() ?: 1f
        )

        val stepX = width / (maxPoints - 1).toFloat()

        fun drawGraph(history: List<Float>, color: Color) {
            if (history.size < 2) return
            val path = Path()
            path.moveTo(0f, height - (history[0] / maxVal) * height)
            for (i in 1 until history.size) {
                val x = i * stepX
                val y = height - (history[i] / maxVal) * height
                path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 4f))
        }

        drawGraph(rxHistory, Color(0xFF00E5FF)) // Download (Cyan)
        drawGraph(txHistory, Color(0xFFFF3D00)) // Upload (Red)
    }
}
