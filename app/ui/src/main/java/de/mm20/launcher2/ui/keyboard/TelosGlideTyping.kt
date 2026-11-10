package de.mm20.launcher2.ui.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

object TelosGlideTyping {
    val currentPathPoints = mutableStateListOf<Offset>()

    fun startSwipe() {
        currentPathPoints.clear()
    }

    fun addPoint(point: Offset) {
        currentPathPoints.add(point)
    }

    fun finishSwipe(lang: KeyboardLanguage, onWordRecognized: (String) -> Unit) {
        if (currentPathPoints.size < 5) {
            currentPathPoints.clear()
            return
        }

        // Mock/Heuristic swipe matcher: pick top suggestion or default word
        val suggestions = TelosKeyboardDictionary.suggestions("καλη", lang)
        val word = suggestions.firstOrNull() ?: if (lang == KeyboardLanguage.Greek) "καλημέρα" else "hello"
        onWordRecognized(word)
        currentPathPoints.clear()
    }
}

@Composable
fun GlideTypingCanvas(
    lang: KeyboardLanguage,
    onWordRecognized: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pathColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(lang) {
                detectDragGestures(
                    onDragStart = { offset ->
                        TelosGlideTyping.startSwipe()
                        TelosGlideTyping.addPoint(offset)
                    },
                    onDrag = { change, _ ->
                        TelosGlideTyping.addPoint(change.position)
                    },
                    onDragEnd = {
                        TelosGlideTyping.finishSwipe(lang, onWordRecognized)
                    },
                    onDragCancel = {
                        TelosGlideTyping.currentPathPoints.clear()
                    }
                )
            }
    ) {
        val points = TelosGlideTyping.currentPathPoints
        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            drawPath(
                path = path,
                color = pathColor,
                style = Stroke(
                    width = 8.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
