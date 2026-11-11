package de.mm20.launcher2.ui.component

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hardware-Accelerated Frosted Glass Blur Surface for Android 12+ (API 31+).
 * Gracefully falls back to translucent dark glass on older Android APIs.
 */
fun Modifier.telosGlassBlur(
    blurRadius: Dp = 16.dp,
    fallbackColor: Color = Color(0xFF0D0E15).copy(alpha = 0.88f)
): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val radiusPx = blurRadius.value * 2.5f
    this
        .graphicsLayer {
            renderEffect = RenderEffect
                .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
        .background(fallbackColor.copy(alpha = 0.75f))
} else {
    this.background(fallbackColor)
}
