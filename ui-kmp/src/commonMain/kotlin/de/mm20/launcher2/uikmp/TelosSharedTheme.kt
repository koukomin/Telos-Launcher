package de.mm20.launcher2.uikmp

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Helix palette primitives. Prefer [TelosSurfaceTokens] role accessors
 * (`panelGlass`, `cardOpaque`, `popupOpaque`, `hudLabel`, `techPill`) for chrome.
 * Defaults are Quiet Ops (dark). Day/night Paper Ops updates these via [applyPalette]
 * so Compose readers recompose without a second theme tree.
 */
object TelosSharedColors {
    var background by mutableStateOf(Color(0xFF0E0B16))
    var surface by mutableStateOf(Color(0xFF1A1528))
    var surfaceContainer by mutableStateOf(Color(0xFF241E36))
    var surfaceHigh by mutableStateOf(Color(0xFF2E2744))
    var panel by mutableStateOf(Color(0xF2241E36))
    var menu by mutableStateOf(Color(0xFF241E36))
    var outline by mutableStateOf(Color(0xFF4A4266))
    var primary by mutableStateOf(Color(0xFFFF007F))
    var secondary by mutableStateOf(Color(0xFF00F0FF))
    var onSurface by mutableStateOf(Color(0xFFF4F0FF))
    var card by mutableStateOf(Color(0xFF1E1830))
    /** True when Paper Ops (light) palette is active. */
    var light by mutableStateOf(false)

    fun applyPalette(
        background: Color,
        surface: Color,
        surfaceContainer: Color,
        surfaceHigh: Color,
        panel: Color,
        menu: Color,
        outline: Color,
        primary: Color,
        secondary: Color,
        onSurface: Color,
        card: Color,
        light: Boolean,
    ) {
        this.background = background
        this.surface = surface
        this.surfaceContainer = surfaceContainer
        this.surfaceHigh = surfaceHigh
        this.panel = panel
        this.menu = menu
        this.outline = outline
        this.primary = primary
        this.secondary = secondary
        this.onSurface = onSurface
        this.card = card
        this.light = light
    }
}

fun telosSharedColorScheme(accent: Color = TelosSharedColors.primary): ColorScheme {
    val scheme = if (TelosSharedColors.light) {
        lightColorScheme(
            primary = accent,
            secondary = TelosSharedColors.secondary,
            background = TelosSharedColors.background,
            surface = TelosSharedColors.surface,
            surfaceContainer = TelosSharedColors.surfaceContainer,
            surfaceContainerHigh = TelosSharedColors.surfaceHigh,
            outline = TelosSharedColors.outline,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = TelosSharedColors.onSurface,
            onSurface = TelosSharedColors.onSurface,
        )
    } else {
        darkColorScheme(
            primary = accent,
            secondary = TelosSharedColors.secondary,
            background = TelosSharedColors.background,
            surface = TelosSharedColors.surface,
            surfaceContainer = TelosSharedColors.surfaceContainer,
            surfaceContainerHigh = TelosSharedColors.surfaceHigh,
            outline = TelosSharedColors.outline,
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onBackground = TelosSharedColors.onSurface,
            onSurface = TelosSharedColors.onSurface,
        )
    }
    return scheme
}

private val telosTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp),
)

@Composable
fun TelosSharedTheme(accent: Color = TelosSharedColors.primary, content: @Composable () -> Unit) {
    // Read reactive palette so day/night flips recompose MaterialTheme.
    val scheme = telosSharedColorScheme(accent)
    MaterialTheme(
        colorScheme = scheme.copy(
            // Keep surfaceContainer opaque regardless of light/dark (popup contract).
            surfaceContainer = TelosSharedColors.surfaceContainer.copy(alpha = 1f),
        ),
        typography = telosTypography,
        content = content,
    )
}
