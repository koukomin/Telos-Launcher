package com.telos.desktop.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TelosColors {
    val background = Color(0xFF0E0B16)
    val surface = Color(0xFF1A1528)
    val surfaceContainer = Color(0xFF241E36)
    val primary = Color(0xFFFF007F)
    val secondary = Color(0xFF00F0FF)
    val onSurface = Color(0xFFF4F0FF)
    val card = Color(0xCC1E1830)
}

fun telosColorScheme(accent: Color = TelosColors.primary): ColorScheme = darkColorScheme(
    primary = accent,
    secondary = TelosColors.secondary,
    background = TelosColors.background,
    surface = TelosColors.surface,
    surfaceContainer = TelosColors.surfaceContainer,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = TelosColors.onSurface,
    onSurface = TelosColors.onSurface,
)

private val telosTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp),
)

@Composable
fun TelosDesktopTheme(accent: Color = TelosColors.primary, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = telosColorScheme(accent),
        typography = telosTypography,
        content = content,
    )
}
