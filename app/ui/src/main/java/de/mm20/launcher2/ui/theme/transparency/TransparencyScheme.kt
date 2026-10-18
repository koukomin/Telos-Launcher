package de.mm20.launcher2.ui.theme.transparency

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import de.mm20.launcher2.themes.transparencies.Transparencies
import de.mm20.launcher2.ui.locals.LocalWallpaperColors


@Composable
fun transparencySchemeOf(transparencies: Transparencies, dark: Boolean? = null): TransparencyScheme {
    val extra = wallpaperGlassExtra(dark ?: de.mm20.launcher2.ui.locals.LocalDarkTheme.current)
    return remember(transparencies, extra) {
        TransparencyScheme(
            background = ((transparencies.background ?: 0.22f) + extra).coerceIn(0.16f, 0.88f),
            surface = ((transparencies.surface ?: 0.48f) + extra * 0.45f).coerceIn(0.28f, 0.92f),
            elevatedSurface = ((transparencies.elevatedSurface ?: 0.62f) + extra * 0.35f).coerceIn(0.40f, 0.95f),
        )
    }
}

/** Extra glass when a dark theme sits on a light wallpaper (or the reverse). */
@Composable
fun wallpaperGlassExtra(dark: Boolean): Float {
    val wallpaper = LocalWallpaperColors.current
    return when {
        dark && wallpaper.supportsDarkText -> 0.40f
        !dark && !wallpaper.supportsDarkText -> 0.34f
        else -> 0f
    }
}

data class TransparencyScheme(
    val background: Float = 0.22f,
    val surface: Float = 0.48f,
    val elevatedSurface: Float = 0.62f,
)

val LocalTransparencyScheme = compositionLocalOf { TransparencyScheme() }

val MaterialTheme.transparency
    @Composable
    get() = LocalTransparencyScheme.current