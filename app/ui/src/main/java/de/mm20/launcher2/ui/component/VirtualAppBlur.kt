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
    .copy(alpha = MaterialTheme.transparency.elevatedSurface)

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
    val surfaceAlpha = MaterialTheme.transparency.surface.coerceIn(0f, 1f)
    val elevatedAlpha = MaterialTheme.transparency.elevatedSurface.coerceIn(0f, 1f)
    val colors = remember(base, surfaceAlpha, elevatedAlpha) { base.copy(
        background = Color.Transparent, surface = Color.Transparent, surfaceContainer = Color.Transparent,
        surfaceContainerLowest = base.surfaceContainerLowest.copy(alpha = surfaceAlpha),
        surfaceContainerLow = base.surfaceContainerLow.copy(alpha = surfaceAlpha),
        surfaceContainerHigh = base.surfaceContainerHigh.copy(alpha = elevatedAlpha),
        surfaceContainerHighest = base.surfaceContainerHighest.copy(alpha = elevatedAlpha),
    ) }
    CompositionLocalProvider(LocalVirtualAppBlur provides token, LocalGlassSurface provides true) {
        MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            Box(Modifier.fillMaxSize().frostedGlass(backing)) { content() }
        }
    }
}
