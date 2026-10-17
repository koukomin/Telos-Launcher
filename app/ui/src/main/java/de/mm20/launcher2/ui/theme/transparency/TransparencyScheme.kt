package de.mm20.launcher2.ui.theme.transparency

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import de.mm20.launcher2.themes.transparencies.Transparencies


@Composable
fun transparencySchemeOf(transparencies: Transparencies): TransparencyScheme {
    return remember(transparencies) {
        TransparencyScheme(
            background = transparencies.background ?: 0.22f,
            surface = transparencies.surface ?: 0.48f,
            elevatedSurface = transparencies.elevatedSurface ?: 0.62f,
        )
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