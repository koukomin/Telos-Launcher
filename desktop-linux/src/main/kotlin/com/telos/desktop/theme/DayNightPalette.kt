package com.telos.desktop.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import de.mm20.launcher2.uikmp.TelosSharedColors

/**
 * Applies Paper Ops (day / light) or Quiet Ops (night / dark) to [TelosSharedColors].
 * Popups stay opaque `surfaceContainer` — never clear that role.
 */
object DayNightPalette {
    var phase by mutableStateOf(DayNightPhase.QuietOps)
        private set

    /** Quiet Ops — dark charcoal + neon accents (default). */
    private val quiet = Palette(
        background = Color(0xFF0E0B16),
        surface = Color(0xFF1A1528),
        surfaceContainer = Color(0xFF241E36),
        surfaceHigh = Color(0xFF2E2744),
        panel = Color(0xF2241E36),
        menu = Color(0xFF241E36),
        outline = Color(0xFF4A4266),
        primary = Color(0xFFFF007F),
        secondary = Color(0xFF00F0FF),
        onSurface = Color(0xFFF4F0FF),
        card = Color(0xFF1E1830),
    )

    /**
     * Paper Ops — cool-warm paper + muted cyan (not cream+terracotta AI default).
     * surfaceContainer stays fully opaque for menus over glass.
     */
    private val paper = Palette(
        background = Color(0xFFE8E9ED),
        surface = Color(0xFFF3F4F7),
        surfaceContainer = Color(0xFFE2E4EA),
        surfaceHigh = Color(0xFFD5D8E0),
        panel = Color(0xF2E2E4EA),
        menu = Color(0xFFE2E4EA),
        outline = Color(0xFF9AA0AE),
        primary = Color(0xFFC2185B),
        secondary = Color(0xFF00838F),
        onSurface = Color(0xFF1A1C22),
        card = Color(0xFFEBECF0),
    )

    fun apply(phase: DayNightPhase) {
        this.phase = phase
        val p = if (phase == DayNightPhase.PaperOps) paper else quiet
        TelosSharedColors.applyPalette(
            background = p.background,
            surface = p.surface,
            surfaceContainer = p.surfaceContainer,
            surfaceHigh = p.surfaceHigh,
            panel = p.panel,
            menu = p.menu,
            outline = p.outline,
            primary = p.primary,
            secondary = p.secondary,
            onSurface = p.onSurface,
            card = p.card,
            light = phase == DayNightPhase.PaperOps,
        )
    }

    private data class Palette(
        val background: Color,
        val surface: Color,
        val surfaceContainer: Color,
        val surfaceHigh: Color,
        val panel: Color,
        val menu: Color,
        val outline: Color,
        val primary: Color,
        val secondary: Color,
        val onSurface: Color,
        val card: Color,
    )
}
