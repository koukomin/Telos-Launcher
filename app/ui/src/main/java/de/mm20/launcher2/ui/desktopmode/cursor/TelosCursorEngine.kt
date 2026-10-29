// === TELOS_PENDING_REVIEW_START: desktop_wm_klassy_sweet_theming ===
package de.mm20.launcher2.ui.desktopmode.cursor

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CursorType {
    DEFAULT_ARROW,
    POINTER_HAND,
    TEXT_IBEAM,
    RESIZE_NS,
    RESIZE_EW,
    RESIZE_NWSE,
    RESIZE_NESW,
    GRAB,
    GRABBING,
    LOADING_RING
}

enum class CursorThemePreset {
    SWEET_DARK_NEON,
    SWEET_PURPLE,
    BREEZE_DARK,
    BREEZE_LIGHT,
    KLASSY_DYNAMIC
}

data class CursorConfig(
    val preset: CursorThemePreset = CursorThemePreset.SWEET_DARK_NEON,
    val size: Dp = 32.dp,
    val showClickPulse: Boolean = true,
    val showNeonGlow: Boolean = true,
    val customAccent: Color? = null
)

object LocalTelosCursorState {
    val currentType = MutableStateFlow(CursorType.DEFAULT_ARROW)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TelosDesktopCanvas(
    config: CursorConfig,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var cursorPos by remember { mutableStateOf(Offset(-100f, -100f)) }
    var cursorType by remember { mutableStateOf(CursorType.DEFAULT_ARROW) }
    var isPressed by remember { mutableStateOf(false) }

    val activeType by LocalTelosCursorState.currentType.collectAsState()
    LaunchedEffect(activeType) { cursorType = activeType }

    // Loading Ring Animation
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    // Click pulse animation
    var pulseTrigger by remember { mutableLongStateOf(0L) }
    val pulseProgress = remember { Animatable(0f) }
    LaunchedEffect(pulseTrigger) {
        if (pulseTrigger > 0L && config.showClickPulse) {
            pulseProgress.snapTo(0f)
            pulseProgress.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing))
        }
    }

    val primaryAccent = config.customAccent ?: MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val position = event.changes.firstOrNull()?.position ?: Offset.Zero
                        cursorPos = position

                        when (event.type) {
                            PointerEventType.Move -> { /* updated position */ }
                            PointerEventType.Press -> {
                                isPressed = true
                                pulseTrigger = System.currentTimeMillis()
                            }
                            PointerEventType.Release -> {
                                isPressed = false
                            }
                        }
                    }
                }
            }
    ) {
        content()

        // Render software custom cursor overlay when position is valid
        if (cursorPos.x >= 0f && cursorPos.y >= 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseSizePx = config.size.toPx()

                // Render click pulse ripple
                if (config.showClickPulse && pulseProgress.value > 0f && pulseProgress.value < 1f) {
                    val pulseRadius = baseSizePx * (0.8f + pulseProgress.value * 1.2f)
                    val alpha = 1f - pulseProgress.value
                    drawCircle(
                        color = Color(0xFF00F0FF).copy(alpha = alpha * 0.8f),
                        radius = pulseRadius,
                        center = cursorPos,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                when (cursorType) {
                    CursorType.DEFAULT_ARROW, CursorType.GRAB, CursorType.GRABBING -> {
                        drawSweetArrowCursor(
                            pos = cursorPos,
                            sizePx = baseSizePx,
                            preset = config.preset,
                            showGlow = config.showNeonGlow,
                            isPressed = isPressed,
                            primaryAccent = primaryAccent
                        )
                    }
                    CursorType.POINTER_HAND -> {
                        drawSweetHandCursor(
                            pos = cursorPos,
                            sizePx = baseSizePx,
                            preset = config.preset,
                            primaryAccent = primaryAccent
                        )
                    }
                    CursorType.TEXT_IBEAM -> {
                        drawIBeamCursor(
                            pos = cursorPos,
                            sizePx = baseSizePx,
                            preset = config.preset,
                            primaryAccent = primaryAccent
                        )
                    }
                    CursorType.RESIZE_NS, CursorType.RESIZE_EW, CursorType.RESIZE_NWSE, CursorType.RESIZE_NESW -> {
                        drawResizeArrowCursor(
                            pos = cursorPos,
                            sizePx = baseSizePx,
                            type = cursorType,
                            preset = config.preset,
                            primaryAccent = primaryAccent
                        )
                    }
                    CursorType.LOADING_RING -> {
                        drawLoadingRingCursor(
                            pos = cursorPos,
                            sizePx = baseSizePx,
                            angle = spinAngle,
                            preset = config.preset,
                            primaryAccent = primaryAccent
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSweetArrowCursor(
    pos: Offset,
    sizePx: Float,
    preset: CursorThemePreset,
    showGlow: Boolean,
    isPressed: Boolean,
    primaryAccent: Color
) {
    val path = Path().apply {
        moveTo(pos.x, pos.y)
        lineTo(pos.x + sizePx * 0.70f, pos.y + sizePx * 0.70f)
        lineTo(pos.x + sizePx * 0.42f, pos.y + sizePx * 0.70f)
        lineTo(pos.x + sizePx * 0.62f, pos.y + sizePx)
        lineTo(pos.x + sizePx * 0.48f, pos.y + sizePx * 1.06f)
        lineTo(pos.x + sizePx * 0.28f, pos.y + sizePx * 0.76f)
        lineTo(pos.x, pos.y + sizePx * 0.95f)
        close()
    }

    val (coreColor, strokeBrush) = when (preset) {
        CursorThemePreset.SWEET_DARK_NEON -> Pair(
            Color(0xFF181623),
            Brush.linearGradient(
                colors = listOf(Color(0xFFFF007F), Color(0xFF00F0FF)),
                start = pos,
                end = Offset(pos.x + sizePx, pos.y + sizePx)
            )
        )
        CursorThemePreset.SWEET_PURPLE -> Pair(
            Color(0xFF1E1B2E),
            Brush.linearGradient(
                colors = listOf(Color(0xFF818CF8), Color(0xFFFF007F)),
                start = pos,
                end = Offset(pos.x + sizePx, pos.y + sizePx)
            )
        )
        CursorThemePreset.BREEZE_DARK -> Pair(
            Color(0xFF232629),
            SolidColor(Color.White)
        )
        CursorThemePreset.BREEZE_LIGHT -> Pair(
            Color.White,
            SolidColor(Color(0xFF232629))
        )
        CursorThemePreset.KLASSY_DYNAMIC -> Pair(
            Color(0xFF121318),
            SolidColor(primaryAccent)
        )
    }

    // Optional neon glow drop-shadow
    if (showGlow && (preset == CursorThemePreset.SWEET_DARK_NEON || preset == CursorThemePreset.SWEET_PURPLE)) {
        drawPath(
            path = path,
            brush = strokeBrush,
            alpha = 0.35f,
            style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // Core fill
    drawPath(path = path, color = coreColor)

    // Outer neon border
    drawPath(
        path = path,
        brush = strokeBrush,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSweetHandCursor(
    pos: Offset,
    sizePx: Float,
    preset: CursorThemePreset,
    primaryAccent: Color
) {
    val radius = sizePx * 0.35f
    val center = Offset(pos.x + sizePx * 0.3f, pos.y + sizePx * 0.3f)

    val strokeBrush = when (preset) {
        CursorThemePreset.SWEET_DARK_NEON -> Brush.linearGradient(listOf(Color(0xFFFF007F), Color(0xFF00F0FF)))
        CursorThemePreset.SWEET_PURPLE -> Brush.linearGradient(listOf(Color(0xFF818CF8), Color(0xFFFF007F)))
        else -> SolidColor(primaryAccent)
    }

    drawCircle(color = Color(0xFF181623), radius = radius, center = center)
    drawCircle(brush = strokeBrush, radius = radius, center = center, style = Stroke(width = 2.5.dp.toPx()))
    drawCircle(brush = strokeBrush, radius = radius * 0.3f, center = center)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIBeamCursor(
    pos: Offset,
    sizePx: Float,
    preset: CursorThemePreset,
    primaryAccent: Color
) {
    val h = sizePx * 0.8f
    val w = sizePx * 0.3f
    val cx = pos.x
    val cy = pos.y

    val color = when (preset) {
        CursorThemePreset.SWEET_DARK_NEON -> Color(0xFF00F0FF)
        CursorThemePreset.SWEET_PURPLE -> Color(0xFFFF007F)
        else -> primaryAccent
    }

    drawLine(color = color, start = Offset(cx - w / 2, cy - h / 2), end = Offset(cx + w / 2, cy - h / 2), strokeWidth = 2.dp.toPx())
    drawLine(color = color, start = Offset(cx, cy - h / 2), end = Offset(cx, cy + h / 2), strokeWidth = 2.dp.toPx())
    drawLine(color = color, start = Offset(cx - w / 2, cy + h / 2), end = Offset(cx + w / 2, cy + h / 2), strokeWidth = 2.dp.toPx())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawResizeArrowCursor(
    pos: Offset,
    sizePx: Float,
    type: CursorType,
    preset: CursorThemePreset,
    primaryAccent: Color
) {
    val color = when (preset) {
        CursorThemePreset.SWEET_DARK_NEON -> Color(0xFF00F0FF)
        CursorThemePreset.SWEET_PURPLE -> Color(0xFF818CF8)
        else -> primaryAccent
    }

    val len = sizePx * 0.5f
    val (dx, dy) = when (type) {
        CursorType.RESIZE_EW -> Pair(len, 0f)
        CursorType.RESIZE_NS -> Pair(0f, len)
        CursorType.RESIZE_NWSE -> Pair(len * 0.7f, len * 0.7f)
        CursorType.RESIZE_NESW -> Pair(len * 0.7f, -len * 0.7f)
        else -> Pair(len, 0f)
    }

    drawLine(color = Color(0xFF181623), start = Offset(pos.x - dx, pos.y - dy), end = Offset(pos.x + dx, pos.y + dy), strokeWidth = 4.dp.toPx())
    drawLine(color = color, start = Offset(pos.x - dx, pos.y - dy), end = Offset(pos.x + dx, pos.y + dy), strokeWidth = 2.dp.toPx())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLoadingRingCursor(
    pos: Offset,
    sizePx: Float,
    angle: Float,
    preset: CursorThemePreset,
    primaryAccent: Color
) {
    val r = sizePx * 0.4f
    val brush = when (preset) {
        CursorThemePreset.SWEET_DARK_NEON -> Brush.sweepGradient(listOf(Color(0xFFFF007F), Color(0xFF00F0FF), Color(0xFFFF007F)))
        else -> SolidColor(primaryAccent)
    }

    drawArc(
        brush = brush,
        startAngle = angle,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = Offset(pos.x - r, pos.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )
}
// === TELOS_PENDING_REVIEW_END: desktop_wm_klassy_sweet_theming ===
