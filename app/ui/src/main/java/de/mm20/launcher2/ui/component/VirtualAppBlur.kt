package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import de.mm20.launcher2.ui.theme.transparency.transparency

/** Compatibility token for screen chrome; all pixels come from the shared provider. */
class VirtualAppBlur internal constructor()
@Composable fun rememberVirtualAppBlur(): VirtualAppBlur = remember { VirtualAppBlur() }
val LocalVirtualAppBlur = staticCompositionLocalOf<VirtualAppBlur?> { null }

@Composable fun virtualAppSurface(): Color = if (LocalVirtualAppBlur.current != null) Color.Transparent
    else MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.transparency.background)
@Composable fun virtualAppElevated(): Color = MaterialTheme.colorScheme.surfaceContainerHigh
    .copy(alpha = 1f)

@Composable fun VirtualAppWindow(content: @Composable () -> Unit) {
    if (LocalGlassSurface.current) content() else VirtualAppTheme(content)
}
@Suppress("UNUSED_PARAMETER")
fun Modifier.virtualAppGlass(blur: VirtualAppBlur, color: Color): Modifier = frostedGlass(color)

/** A single backing, including standalone and inline apps; nested wrappers do not add opacity. */
@Composable fun VirtualAppTheme(content: @Composable () -> Unit) {
    if (LocalGlassSurface.current) { content(); return }
    val token = rememberVirtualAppBlur()
    val base = MaterialTheme.colorScheme
    val alpha = MaterialTheme.transparency.background.coerceIn(0f, 1f)
    val backing = base.surface.copy(alpha = alpha)
    val colors = remember(base) { base.copy(
        background = Color.Transparent,
        // Keep root scaffolds transparent; popup containers must obscure underlying text.
        surface = Color.Transparent,
        surfaceContainer = base.surfaceContainer.copy(alpha = 1f),
        surfaceContainerLowest = base.surfaceContainerLowest.copy(alpha = 1f),
        surfaceContainerLow = base.surfaceContainerLow.copy(alpha = 1f),
        surfaceContainerHigh = base.surfaceContainerHigh.copy(alpha = 1f),
        surfaceContainerHighest = base.surfaceContainerHighest.copy(alpha = 1f),
    ) }
    CompositionLocalProvider(LocalVirtualAppBlur provides token, LocalGlassSurface provides true, androidx.compose.material3.LocalContentColor provides colors.onSurface) {
        MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            Box(Modifier.fillMaxSize().frostedGlass(backing)) { content() }
        }
    }
}
