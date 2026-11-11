package de.mm20.launcher2.theme

import androidx.compose.ui.graphics.Color

enum class CyberThemePreset(val label: String) {
    CyberNeon("Cyber Neon"),
    MatrixTerminal("Matrix Terminal"),
    SolanaViolet("Solana Violet"),
    VaporwaveSunset("Vaporwave Sunset"),
    OLEDStealth("OLED Stealth Dark")
}

data class TelosCyberPalette(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color
)

object TelosCyberPalettes {

    fun get(preset: CyberThemePreset): TelosCyberPalette = when (preset) {
        CyberThemePreset.CyberNeon -> TelosCyberPalette(
            primary = Color(0xFF00F2FE),
            secondary = Color(0xFFFF007F),
            accent = Color(0xFFA855F7),
            background = Color(0xFF0D0E15),
            surface = Color(0xFF161822)
        )
        CyberThemePreset.MatrixTerminal -> TelosCyberPalette(
            primary = Color(0xFF00FF66),
            secondary = Color(0xFF00B344),
            accent = Color(0xFF33FF88),
            background = Color(0xFF050B07),
            surface = Color(0xFF0B1710)
        )
        CyberThemePreset.SolanaViolet -> TelosCyberPalette(
            primary = Color(0xFFA855F7),
            secondary = Color(0xFFF59E0B),
            accent = Color(0xFFEC4899),
            background = Color(0xFF0F0B1E),
            surface = Color(0xFF18122B)
        )
        CyberThemePreset.VaporwaveSunset -> TelosCyberPalette(
            primary = Color(0xFFFF007F),
            secondary = Color(0xFF00F2FE),
            accent = Color(0xFFFFB800),
            background = Color(0xFF140727),
            surface = Color(0xFF210C3F)
        )
        CyberThemePreset.OLEDStealth -> TelosCyberPalette(
            primary = Color(0xFFFFFFFF),
            secondary = Color(0xFF9CA3AF),
            accent = Color(0xFF00F2FE),
            background = Color(0xFF000000),
            surface = Color(0xFF121212)
        )
    }
}
