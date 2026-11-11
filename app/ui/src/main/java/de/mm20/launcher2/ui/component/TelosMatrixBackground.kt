package de.mm20.launcher2.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ultra High-Performance Cached Tactical Cyber Grid Background.
 * Uses drawWithCache to render grid lines without recalculating geometry on every frame pass.
 */
@Composable
fun TelosMatrixBackground(
    modifier: Modifier = Modifier,
    gridColor: Color = Color(0xFF00F2FE),
    gridSpacing: Dp = 32.dp,
    alpha: Float = 0.12f
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val spacingPx = gridSpacing.toPx()
                val width = size.width
                val height = size.height
                val effectiveColor = gridColor.copy(alpha = alpha)
                val nodeColor = gridColor.copy(alpha = (alpha * 1.8f).coerceAtMost(1f))
                val dotRadius = 2.dp.toPx()

                onDrawBehind {
                    // Vertical Grid Lines
                    var x = spacingPx
                    while (x < width) {
                        drawLine(
                            color = effectiveColor,
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = 1f
                        )
                        x += spacingPx
                    }

                    // Horizontal Grid Lines & Intersection Nodes
                    var y = spacingPx
                    while (y < height) {
                        drawLine(
                            color = effectiveColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )

                        var dotX = spacingPx
                        while (dotX < width) {
                            drawCircle(
                                color = nodeColor,
                                radius = dotRadius,
                                center = Offset(dotX, y)
                            )
                            dotX += spacingPx
                        }

                        y += spacingPx
                    }
                }
            }
    ) {}
}
