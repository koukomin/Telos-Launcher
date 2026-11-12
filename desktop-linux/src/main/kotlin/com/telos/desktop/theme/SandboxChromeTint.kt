package com.telos.desktop.theme

import androidx.compose.ui.graphics.Color
import com.telos.desktop.apps.FlatpakSandboxTier

/**
 * Visual OpSec tint for Store / Flatpak permission chrome by sandbox tier.
 * Thin accent ring only — does not change Flatpak policy itself.
 */
object SandboxChromeTint {
    val Host = VaultNeonTokens.accentOrange
    val NetIsolated = VaultNeonTokens.accentCyan
    val VaultFs = VaultNeonTokens.accentGreen
    /** Standard uses magenta; muted variant softens for Quiet Ops. */
    val Standard = VaultNeonTokens.accentMagenta
    val StandardMuted = Color(0xFFB06090)

    fun forTier(tier: FlatpakSandboxTier, intensity: ThemeIntensity = ThemeIntensity.QuietOps): Color =
        when (tier) {
            FlatpakSandboxTier.Host -> Host
            FlatpakSandboxTier.NetIsolated -> NetIsolated
            FlatpakSandboxTier.VaultFs -> VaultFs
            FlatpakSandboxTier.Standard ->
                if (intensity == ThemeIntensity.QuietOps) StandardMuted else Standard
        }

    fun borderAlpha(intensity: ThemeIntensity = ThemeIntensity.QuietOps): Float =
        intensity.techBorderAlpha.coerceIn(0.25f, 0.9f)
}
