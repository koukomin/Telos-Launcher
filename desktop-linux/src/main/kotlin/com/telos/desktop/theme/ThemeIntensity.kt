package com.telos.desktop.theme

import androidx.compose.ui.graphics.Color

/**
 * Vault Neon / Quiet Ops intensity for Telos Desktop chrome.
 * Stored as `theme.intensity` in vault `desktop.properties`.
 */
enum class ThemeIntensity {
    /** Dark charcoal + one neon accent, low glow — daily / screenshare-safe. */
    QuietOps,
    /** Stronger borders and HUD accents — demos / night sessions. */
    VaultNeon,
    /** Optional light paper look — outdoor / daylight (not cream+terracotta). */
    PaperOps,
    ;

    val propertyValue: String
        get() = when (this) {
            QuietOps -> "quiet"
            VaultNeon -> "neon"
            PaperOps -> "paper"
        }

    /** Border glow alpha for telosTechCard / sandbox rings. */
    val techBorderAlpha: Float
        get() = when (this) {
            QuietOps -> 0.38f
            VaultNeon -> 0.85f
            PaperOps -> 0.45f
        }

    companion object {
        fun parse(raw: String?): ThemeIntensity {
            val v = raw?.trim()?.lowercase().orEmpty()
            return when (v) {
                "quiet", "quiet_ops", "quietops", "ops" -> QuietOps
                "neon", "vault", "vault_neon", "vaultneon" -> VaultNeon
                "paper", "paper_ops", "paperops", "light" -> PaperOps
                else -> QuietOps
            }
        }
    }
}

/** Locked-boot / Quiet Ops palette — charcoal, no wallpaper identity. */
object QuietOpsTokens {
    val background = Color(0xFF0A0C0E)
    val surface = Color(0xFF12151A)
    val surfaceContainer = Color(0xFF1A1E24)
    val accent = Color(0xFF00F0FF)
    val onSurface = Color(0xFFE6EEF2)
    val muted = Color(0xFF8A96A0)
}

object VaultNeonTokens {
    val accentGreen = Color(0xFF00E676)
    val accentCyan = Color(0xFF00F0FF)
    val accentMagenta = Color(0xFFFF007F)
    val accentOrange = Color(0xFFFF9100)
}
