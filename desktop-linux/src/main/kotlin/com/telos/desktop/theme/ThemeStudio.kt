package com.telos.desktop.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Quiet Ops (default) vs Vault Neon (+ optional Paper Ops light). */
enum class ThemeIntensity(val propertyValue: String) {
    QuietOps("quiet"),
    VaultNeon("neon"),
    PaperOps("paper"),
    ;

    companion object {
        fun parse(raw: String?): ThemeIntensity = when (raw?.trim()?.lowercase()) {
            "neon", "vault_neon", "vault-neon" -> VaultNeon
            "paper", "paper_ops", "paper-ops", "light" -> PaperOps
            else -> QuietOps
        }
    }
}

/**
 * Neon accent presets plus wallpaper tonal seed (`theme.accent=wallpaper`).
 * Actual seed color is supplied via [DesktopThemeChrome.wallpaperSeedAccent].
 */
enum class ThemeAccent(val propertyValue: String, val color: Color) {
    NeonGreen("00E676", Color(0xFF00E676)),
    NeonCyan("00F0FF", Color(0xFF00F0FF)),
    NeonMagenta("FF007F", Color(0xFFFF007F)),
    NeonOrange("FF9100", Color(0xFFFF9100)),
    FromWallpaper("wallpaper", Color(0xFF00F0FF)),
    ;

    companion object {
        val NEON_PRESETS = listOf(NeonGreen, NeonCyan, NeonMagenta, NeonOrange)

        fun parse(raw: String?): ThemeAccent {
            val v = raw?.trim()?.removePrefix("#")?.lowercase().orEmpty()
            return when (v) {
                "wallpaper", "from_wallpaper", "from-wallpaper", "tonal" -> FromWallpaper
                "00e676", "e676", "green", "neon_green", "neon-green" -> NeonGreen
                "00f0ff", "f0ff", "cyan", "neon_cyan", "neon-cyan" -> NeonCyan
                "ff007f", "007f", "magenta", "neon_magenta", "neon-magenta", "pink" -> NeonMagenta
                "ff9100", "9100", "orange", "neon_orange", "neon-orange" -> NeonOrange
                else -> NeonMagenta
            }
        }

        /** Preserve `wallpaper` and unknown custom values when writing props. */
        fun canonicalizeForStorage(raw: String): String {
            val v = raw.trim()
            if (v.equals("wallpaper", ignoreCase = true)) return "wallpaper"
            if (v.isEmpty()) return NeonMagenta.propertyValue
            return parse(v).propertyValue
        }
    }
}

/** Compact / Comfort / Touch → panel height, icon size, snap/overview pill scale. */
enum class ThemeDensity(
    val propertyValue: String,
    val panelHeightDp: Float,
    val panelIconDp: Float,
    val snapPillScale: Float,
) {
    Compact("compact", panelHeightDp = 32f, panelIconDp = 20f, snapPillScale = 0.85f),
    Comfort("comfort", panelHeightDp = 36f, panelIconDp = 22f, snapPillScale = 1.0f),
    Touch("touch", panelHeightDp = 48f, panelIconDp = 32f, snapPillScale = 1.2f),
    ;

    companion object {
        fun parse(raw: String?): ThemeDensity = when (raw?.trim()?.lowercase()) {
            "compact", "dense" -> Compact
            "touch", "large", "accessibility" -> Touch
            else -> Comfort
        }

        fun nearest(panelHeightDp: Float, panelIconDp: Float): ThemeDensity {
            return entries.minBy { dens ->
                val dh = dens.panelHeightDp - panelHeightDp
                val di = dens.panelIconDp - panelIconDp
                dh * dh + di * di
            }
        }
    }
}

/**
 * Reactive Theme Studio chrome for Helix (border glow, gauges, density scale).
 * Popups stay opaque [TelosColors.menu] / surfaceContainer — no second blur.
 */
object DesktopThemeChrome {
    var intensity by mutableStateOf(ThemeIntensity.QuietOps)
    var accent by mutableStateOf(ThemeAccent.NeonMagenta)
    var density by mutableStateOf(ThemeDensity.Comfort)
    var snapPillScale by mutableStateOf(1.0f)
    /** Local wallpaper tonal seed when [accent] is [ThemeAccent.FromWallpaper]; null = cyan fallback. */
    var wallpaperSeedAccent by mutableStateOf<Color?>(null)

    /** Quiet = low glow; Neon = stronger Cyber-Tech borders. */
    val techBorderAlpha: Float
        get() = when (intensity) {
            ThemeIntensity.QuietOps -> 0.32f
            ThemeIntensity.VaultNeon -> 0.90f
            ThemeIntensity.PaperOps -> 0.40f
        }

    val showGauges: Boolean
        get() = intensity == ThemeIntensity.VaultNeon

    val selectedPillFillAlpha: Float
        get() = when (intensity) {
            ThemeIntensity.QuietOps -> 0.14f
            ThemeIntensity.VaultNeon -> 0.28f
            ThemeIntensity.PaperOps -> 0.18f
        }

    /** Resolved accent for Compose / Helix chrome (wallpaper uses local tonal seed when available). */
    val resolvedAccent: Color
        get() = when {
            accent == ThemeAccent.FromWallpaper ->
                wallpaperSeedAccent ?: ThemeAccent.NeonCyan.color
            intensity == ThemeIntensity.PaperOps && accent == ThemeAccent.NeonMagenta ->
                Color(0xFF00838F) // muted cyan for Paper Ops default
            else -> accent.color
        }

    fun applyFromSettings(
        intensityRaw: String?,
        accentRaw: String?,
        densityRaw: String?,
        snapScale: Float? = null,
    ) {
        intensity = ThemeIntensity.parse(intensityRaw)
        accent = ThemeAccent.parse(accentRaw)
        density = ThemeDensity.parse(densityRaw)
        snapPillScale = (snapScale ?: density.snapPillScale).coerceIn(0.7f, 1.4f)
    }

    fun applyDensity(dens: ThemeDensity) {
        density = dens
        snapPillScale = dens.snapPillScale
    }
}
