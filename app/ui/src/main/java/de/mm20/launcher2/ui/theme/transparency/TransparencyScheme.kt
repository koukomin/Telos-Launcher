package de.mm20.launcher2.ui.theme.transparency

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import de.mm20.launcher2.themes.transparencies.Transparencies

@Composable
fun transparencySchemeOf(transparencies: Transparencies, dark: Boolean? = null): TransparencyScheme {
    return remember(transparencies) {
        TransparencyScheme(
            background = (transparencies.background ?: 0.80f).coerceIn(0f, 1f),
            surface = (transparencies.surface ?: 0.80f).coerceIn(0f, 1f),
            elevatedSurface = (transparencies.elevatedSurface ?: 0.80f).coerceIn(0f, 1f),
        )
    }
}

data class TransparencyScheme(
    val background: Float = 0.80f,
    val surface: Float = 0.80f,
    val elevatedSurface: Float = 0.80f,
)

val LocalTransparencyScheme = compositionLocalOf { TransparencyScheme() }
val MaterialTheme.transparency
    @Composable get() = LocalTransparencyScheme.current
