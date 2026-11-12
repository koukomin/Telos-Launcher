package de.mm20.launcher2.uikmp

import androidx.compose.ui.graphics.Color

/**
 * Shared Helix / Android virtual-app surface chrome kit.
 *
 * Stable role names across `:ui-kmp` (desktop Helix) and `:app:ui`
 * (Android [VirtualAppTheme] / Cyber-Tech). Only [panelGlass] may carry root
 * alpha; [popupOpaque] and [cardOpaque] stay fully opaque so wallpaper text
 * never bleeds through menus or cards.
 *
 * `:app:app` must not depend on `:desktop-linux`; both sides import this module
 * (or matching Android Material accessors with the same role names).
 */
object TelosSurfaceTokens {
    const val ROLE_PANEL_GLASS = "panelGlass"
    const val ROLE_CARD_OPAQUE = "cardOpaque"
    const val ROLE_POPUP_OPAQUE = "popupOpaque"
    const val ROLE_HUD_LABEL = "hudLabel"
    const val ROLE_TECH_PILL = "techPill"

    /** Default panel translucency — root / top-panel chrome only. */
    const val DEFAULT_PANEL_GLASS_ALPHA = 0.94f

    /** Cards, popups and tech pills must remain fully opaque. */
    const val OPAQUE_ALPHA = 1f

    val roles: List<String> = listOf(
        ROLE_PANEL_GLASS,
        ROLE_CARD_OPAQUE,
        ROLE_POPUP_OPAQUE,
        ROLE_HUD_LABEL,
        ROLE_TECH_PILL,
    )

    /**
     * Root / top-panel glass. Alpha is allowed exclusively on this role.
     * Prefer [TelosSharedColors.panel] RGB with an explicit alpha.
     */
    fun panelGlass(
        base: Color = TelosSharedColors.panel,
        alpha: Float = DEFAULT_PANEL_GLASS_ALPHA,
    ): Color = base.copy(alpha = alpha.coerceIn(0f, 1f))

    /** Opaque card plate under neon borders (`surfaceContainerLow` / shared card). */
    val cardOpaque: Color
        get() = TelosSharedColors.card.copy(alpha = OPAQUE_ALPHA)

    /** Opaque popup / menu fill — matches Android `virtualAppPopup()`. */
    val popupOpaque: Color
        get() = TelosSharedColors.menu.copy(alpha = OPAQUE_ALPHA)

    /** HUD / telemetry label accent (dot-matrix style). */
    val hudLabel: Color
        get() = TelosSharedColors.secondary

    /** Unselected tech-pill fill (opaque `surfaceContainer`). */
    val techPill: Color
        get() = TelosSharedColors.surfaceContainer.copy(alpha = OPAQUE_ALPHA)

    fun allowsRootAlpha(role: String): Boolean = role == ROLE_PANEL_GLASS

    fun requiresOpaque(role: String): Boolean =
        role == ROLE_CARD_OPAQUE ||
            role == ROLE_POPUP_OPAQUE ||
            role == ROLE_TECH_PILL

    /** Popup contract: alpha must stay at 1 (no cleared surface for menus). */
    fun isPopupContractIntact(color: Color): Boolean =
        color.alpha >= OPAQUE_ALPHA - 0.001f
}
