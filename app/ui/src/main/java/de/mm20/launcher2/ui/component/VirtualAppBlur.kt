package de.mm20.launcher2.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.ui.theme.transparency.transparency
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.compose.koinInject

data class VirtualAppBlur(
    val haze: HazeState,
    val radius: Int,
)

@Composable
fun rememberVirtualAppBlur(): VirtualAppBlur {
    val uiSettings: UiSettings = koinInject()
    val blurRadius by uiSettings.drawerBackgroundBlur.collectAsStateWithLifecycle(32)
    val enabled = isAtLeastApiLevel(33) && blurRadius > 0
    val haze = rememberHazeState(blurEnabled = enabled)
    return remember(haze, blurRadius, enabled) {
        VirtualAppBlur(haze, if (enabled) blurRadius else 0)
    }
}

val LocalVirtualAppBlur = staticCompositionLocalOf<VirtualAppBlur?> { null }

@Composable
fun virtualAppSurface(): Color {
    val t = MaterialTheme.transparency
    return MaterialTheme.colorScheme.surface.copy(alpha = t.background)
}

@Composable
fun virtualAppElevated(): Color {
    val t = MaterialTheme.transparency
    return MaterialTheme.colorScheme.surface.copy(alpha = t.elevatedSurface)
}

/** Glass window for virtual apps that do not set up haze themselves (Calculator, Notes, …). */
@Composable
fun VirtualAppWindow(content: @Composable () -> Unit) {
    val blur = rememberVirtualAppBlur()
    CompositionLocalProvider(LocalVirtualAppBlur provides blur) {
        Box(Modifier.fillMaxSize().hazeSource(blur.haze).background(virtualAppSurface())) {
            content()
        }
    }
}

fun Modifier.virtualAppGlass(blur: VirtualAppBlur, color: Color): Modifier {
    return if (blur.radius > 0) {
        hazeEffect(blur.haze) {
            blurRadius = blur.radius.dp
            backgroundColor = color
        }.background(color)
    } else {
        background(color)
    }
}
