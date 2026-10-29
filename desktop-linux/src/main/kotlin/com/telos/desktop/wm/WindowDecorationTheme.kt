package com.telos.desktop.wm

import androidx.compose.ui.graphics.Color

enum class WindowButtonStyle {
    SIERRA_MACOS,
    SIERRA_BREEZE_BLEND,
    KLASSY_OUTLINE,
    KLASSY_ICON_ONLY,
    KLASSY_ACCENT_FILLED,
}

data class WindowDecorationTheme(
    val buttonStyle: WindowButtonStyle = WindowButtonStyle.KLASSY_ACCENT_FILLED,
    val buttonsOnLeft: Boolean = false,
    val unisonHoverSymbols: Boolean = true,
    val cornerRadiusPx: Float = 12f,
    val titleBarHeightPx: Float = 36f,
    val glassTitleBar: Boolean = true,
    val activeOutline: Color = Color(0xFF00F0FF),
    val inactiveOutline: Color = Color(0x33FFFFFF),
    val titleBarTint: Color = Color(0xCC1A1528),
    val accent: Color = Color(0xFFFF007F),
)
