package de.mm20.launcher2.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.mm20.launcher2.ui.theme.transparency.transparency
import de.mm20.launcher2.uikmp.TelosSurfaceTokens as SharedSurfaceTokens

/**
 * Android MaterialTheme bindings for the shared Helix surface kit.
 *
 * Role names match [SharedSurfaceTokens] (`panelGlass`, `cardOpaque`,
 * `popupOpaque`, `hudLabel`, `techPill`). [popupOpaque] always delegates to
 * [virtualAppPopup] so menus stay fully opaque over wallpaper glass.
 */
object TelosSurfaceTokens {
    const val ROLE_PANEL_GLASS = SharedSurfaceTokens.ROLE_PANEL_GLASS
    const val ROLE_CARD_OPAQUE = SharedSurfaceTokens.ROLE_CARD_OPAQUE
    const val ROLE_POPUP_OPAQUE = SharedSurfaceTokens.ROLE_POPUP_OPAQUE
    const val ROLE_HUD_LABEL = SharedSurfaceTokens.ROLE_HUD_LABEL
    const val ROLE_TECH_PILL = SharedSurfaceTokens.ROLE_TECH_PILL

    val roles: List<String> = SharedSurfaceTokens.roles

    /** Root scaffold glass — only role that may clear alpha on Android. */
    @Composable
    fun panelGlass(): Color {
        val alpha = MaterialTheme.transparency.background.coerceIn(0.88f, 1f)
        return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
    }

    /** Opaque card plate (`surfaceContainerLow`) under neon borders. */
    @Composable
    fun cardOpaque(): Color =
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = SharedSurfaceTokens.OPAQUE_ALPHA)

    /**
     * Opaque popup / dialog / menu fill. Must remain alpha 1 — do not replace
     * with cleared root `surface`.
     */
    @Composable
    fun popupOpaque(): Color = virtualAppPopup()

    /** HUD / telemetry accent (dot-matrix labels). */
    @Composable
    fun hudLabel(): Color = TelosCyber.NeonCyan

    /** Unselected tech-pill fill. */
    @Composable
    fun techPill(): Color =
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = SharedSurfaceTokens.OPAQUE_ALPHA)
}
