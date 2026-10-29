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
    if (LocalGlassSurface.current) content() else {
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)) + 
                    androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(300), initialScale = 0.92f),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) + 
                   androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(200), targetScale = 0.92f)
        ) {
            VirtualAppTheme(content)
        }
    }
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
        background = base.background.copy(alpha = 0f),
        // Retain RGB so legacy controls restoring opacity keep the correct theme tone.
        // Keep root scaffolds transparent; popup containers must obscure underlying text.
        surface = base.surface.copy(alpha = 0f),
        surfaceContainer = base.surfaceContainer.copy(alpha = 1f),
        surfaceContainerLowest = base.surfaceContainerLowest.copy(alpha = 1f),
        surfaceContainerLow = base.surfaceContainerLow.copy(alpha = 1f),
        surfaceContainerHigh = base.surfaceContainerHigh.copy(alpha = 1f),
        surfaceContainerHighest = base.surfaceContainerHighest.copy(alpha = 1f),
        surfaceBright = base.surfaceBright.copy(alpha = 1f),
        surfaceDim = base.surfaceDim.copy(alpha = 1f),
        surfaceVariant = base.surfaceVariant.copy(alpha = 1f),
    ) }
    CompositionLocalProvider(LocalVirtualAppBlur provides token, LocalGlassSurface provides true, androidx.compose.material3.LocalContentColor provides colors.onSurface) {
        MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            Box(Modifier.fillMaxSize().frostedGlass(backing, fallbackColor = base.surface)) { content() }
        }
    }
}
