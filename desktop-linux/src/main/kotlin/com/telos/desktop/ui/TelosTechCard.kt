package com.telos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.telos.desktop.theme.ThemeIntensity

/**
 * Desktop Cyber-Tech thin accent ring (opaque container — no wallpaper bleed).
 * Mirrors Android `Modifier.telosTechCard` without depending on `:app:ui`.
 */
fun Modifier.telosTechCard(
    borderColor: Color,
    shape: Shape = RoundedCornerShape(12.dp),
    borderWidth: Dp = 1.2.dp,
    alpha: Float = 0.85f,
    backgroundColor: Color = TelosColors.surfaceContainer,
): Modifier = this
    .clip(shape)
    .border(borderWidth, borderColor.copy(alpha = alpha.coerceIn(0f, 1f)), shape)
    .background(backgroundColor)

fun Modifier.sandboxTierCard(
    borderColor: Color,
    intensity: ThemeIntensity = ThemeIntensity.QuietOps,
    shape: Shape = RoundedCornerShape(12.dp),
): Modifier = telosTechCard(
    borderColor = borderColor,
    shape = shape,
    borderWidth = 1.2.dp,
    alpha = intensity.techBorderAlpha,
    backgroundColor = TelosColors.surfaceContainer,
)
