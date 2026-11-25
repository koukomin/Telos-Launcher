package de.mm20.launcher2.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Single Source of Truth Theme Registry for Telos Cyber-Tech design language.
 */
@Immutable
object TelosThemeRegistry {
    // Neon Cyber Accents
    val NeonGreen = Color(0xFF00E676)
    val NeonCyan = Color(0xFF00F0FF)
    val NeonMagenta = Color(0xFFFF007F)
    val NeonOrange = Color(0xFFFF9100)
    val NeonRed = Color(0xFFFF1744)
    val NeonPurple = Color(0xFFD500F9)

    // Surface Glass Tones
    val GlassDarkBackground = Color(0xFF12131C)
    val GlassSurfaceContainer = Color(0xFF1C1D2A)
    val GlassBorderLight = Color(0x33FFFFFF)

    // Primary Accents Palette
    val CyberAccents = listOf(
        NeonCyan,
        NeonGreen,
        NeonMagenta,
        NeonOrange,
        NeonRed,
        NeonPurple
    )
}
