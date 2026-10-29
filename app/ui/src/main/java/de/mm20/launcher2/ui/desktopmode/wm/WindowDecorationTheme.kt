// === TELOS_PENDING_REVIEW_START: desktop_wm_klassy_sweet_theming ===
package de.mm20.launcher2.ui.desktopmode.wm

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window Button Styles replicating KDE Klassy and SierraBreezeEnhanced.
 */
enum class WindowButtonStyle {
    /** Traffic light circles (Red=Close, Yellow=Minimize, Green=Maximize) with hover symbols */
    SIERRA_MACOS,
    /** Traffic light colors with Breeze-style crisp vector icons inside */
    SIERRA_BREEZE_BLEND,
    /** Thin geometric outlines that dynamically fill with accent/semantic colors on hover */
    KLASSY_OUTLINE,
    /** Pure minimalist vector glyphs with customizable background highlight shapes on hover */
    KLASSY_ICON_ONLY,
    /** Solid accent-colored buttons with contrasting cutout icons */
    KLASSY_ACCENT_FILLED
}

enum class ButtonPosition {
    LEFT,
    RIGHT
}

enum class BackdropStyle {
    SOLID,
    GRADIENT,
    TRANSLUCENT_GLASS
}

/**
 * Presets for Window Decoration Themes.
 */
enum class WindowDecorationPreset {
    SIERRA_MACOS_DARK,
    SIERRA_BREEZE_ENHANCED,
    KLASSY_NEON_OUTLINE,
    KLASSY_MINIMAL,
    KLASSY_ACCENT_FULL
}

/**
 * Configuration for Window Decoration Frame and Titlebar.
 */
data class WindowThemeConfig(
    val preset: WindowDecorationPreset = WindowDecorationPreset.SIERRA_MACOS_DARK,
    val buttonStyle: WindowButtonStyle = WindowButtonStyle.SIERRA_MACOS,
    val buttonPosition: ButtonPosition = ButtonPosition.LEFT,
    val cornerRadiusTop: Dp = 12.dp,
    val cornerRadiusBottom: Dp = 12.dp,
    val borderWidth: Dp = 1.5.dp,
    val activeBorderColor: Color? = null, // null defaults to Monet primary accent
    val inactiveBorderColor: Color = Color.Gray.copy(alpha = 0.3f),
    val borderGlowIntensity: Float = 0.6f,
    val titlebarOpacity: Float = 0.90f,
    val titlebarBackdropStyle: BackdropStyle = BackdropStyle.TRANSLUCENT_GLASS,
    val unisonHoverEffect: Boolean = true, // SierraBreezeEnhanced feature
    val showTitleSeparator: Boolean = true,
    val buttonSpacing: Dp = 8.dp,
    val buttonMargin: Dp = 8.dp
)

object WindowThemePresets {
    val SierraMacOsDark = WindowThemeConfig(
        preset = WindowDecorationPreset.SIERRA_MACOS_DARK,
        buttonStyle = WindowButtonStyle.SIERRA_MACOS,
        buttonPosition = ButtonPosition.LEFT,
        cornerRadiusTop = 12.dp,
        cornerRadiusBottom = 12.dp,
        borderWidth = 1.5.dp,
        titlebarOpacity = 0.90f,
        titlebarBackdropStyle = BackdropStyle.TRANSLUCENT_GLASS,
        unisonHoverEffect = true
    )

    val SierraBreezeEnhanced = WindowThemeConfig(
        preset = WindowDecorationPreset.SIERRA_BREEZE_ENHANCED,
        buttonStyle = WindowButtonStyle.SIERRA_BREEZE_BLEND,
        buttonPosition = ButtonPosition.RIGHT,
        cornerRadiusTop = 10.dp,
        cornerRadiusBottom = 10.dp,
        borderWidth = 1.5.dp,
        titlebarOpacity = 0.92f,
        titlebarBackdropStyle = BackdropStyle.GRADIENT,
        unisonHoverEffect = true
    )

    val KlassyNeonOutline = WindowThemeConfig(
        preset = WindowDecorationPreset.KLASSY_NEON_OUTLINE,
        buttonStyle = WindowButtonStyle.KLASSY_OUTLINE,
        buttonPosition = ButtonPosition.RIGHT,
        cornerRadiusTop = 14.dp,
        cornerRadiusBottom = 14.dp,
        borderWidth = 2.0.dp,
        borderGlowIntensity = 0.9f,
        titlebarOpacity = 0.88f,
        titlebarBackdropStyle = BackdropStyle.TRANSLUCENT_GLASS,
        unisonHoverEffect = false
    )

    val KlassyMinimal = WindowThemeConfig(
        preset = WindowDecorationPreset.KLASSY_MINIMAL,
        buttonStyle = WindowButtonStyle.KLASSY_ICON_ONLY,
        buttonPosition = ButtonPosition.RIGHT,
        cornerRadiusTop = 6.dp,
        cornerRadiusBottom = 6.dp,
        borderWidth = 1.0.dp,
        borderGlowIntensity = 0.3f,
        titlebarOpacity = 0.95f,
        titlebarBackdropStyle = BackdropStyle.SOLID,
        unisonHoverEffect = false
    )

    val KlassyAccentFull = WindowThemeConfig(
        preset = WindowDecorationPreset.KLASSY_ACCENT_FULL,
        buttonStyle = WindowButtonStyle.KLASSY_ACCENT_FILLED,
        buttonPosition = ButtonPosition.RIGHT,
        cornerRadiusTop = 16.dp,
        cornerRadiusBottom = 16.dp,
        borderWidth = 2.0.dp,
        borderGlowIntensity = 0.8f,
        titlebarOpacity = 0.92f,
        titlebarBackdropStyle = BackdropStyle.TRANSLUCENT_GLASS,
        unisonHoverEffect = false
    )

    fun forPreset(preset: WindowDecorationPreset): WindowThemeConfig = when (preset) {
        WindowDecorationPreset.SIERRA_MACOS_DARK -> SierraMacOsDark
        WindowDecorationPreset.SIERRA_BREEZE_ENHANCED -> SierraBreezeEnhanced
        WindowDecorationPreset.KLASSY_NEON_OUTLINE -> KlassyNeonOutline
        WindowDecorationPreset.KLASSY_MINIMAL -> KlassyMinimal
        WindowDecorationPreset.KLASSY_ACCENT_FULL -> KlassyAccentFull
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_wm_klassy_sweet_theming ===
