package com.telos.desktop.theme

import java.util.Properties

/**
 * Helix top-panel surface appearance (transparent / blurred / solid).
 *
 * Preference key: [PREF_KEY] (`panel.surfaceMode`). Only the **panel root** may
 * clear alpha or show frost; popups / menus stay opaque `surfaceContainer`.
 *
 * Blurred mode stores [panelBlur] for wallpaper frost. Compose Desktop cannot
 * sample host wallpaper behind the panel on every compositor, so rendering uses
 * a best-effort translucent frost plate when host backdrop blur is unavailable
 * ([hostBackdropBlurAvailable] is currently false).
 */
enum class PanelSurfaceMode(val propertyValue: String) {
    Transparent("transparent"),
    Blurred("blurred"),
    Solid("solid"),
    ;

    companion object {
        const val PREF_KEY = "panel.surfaceMode"

        /** Host/compositor wallpaper backdrop blur — not available on current labwc path. */
        const val HOST_BACKDROP_BLUR_AVAILABLE = false

        fun parse(raw: String?): PanelSurfaceMode = when (raw?.trim()?.lowercase()) {
            "blurred", "blur", "frost", "frosted", "glass" -> Blurred
            "solid", "opaque" -> Solid
            "transparent", "clear", "glass_clear", "glass-clear" -> Transparent
            // Preserve near-opaque + blur defaults from older installs as Blurred.
            else -> Blurred
        }

        fun parse(p: Properties, default: PanelSurfaceMode = Blurred): PanelSurfaceMode {
            val raw = p.getProperty(PREF_KEY) ?: return default
            return parse(raw)
        }
    }
}

/**
 * Effective alpha / blur after applying [PanelSurfaceMode] to stored preferences.
 */
data class PanelSurfaceResolved(
    val mode: PanelSurfaceMode,
    /** Plate alpha drawn on the panel root (0..1). */
    val alpha: Float,
    /** Stored frost radius in dp-ish units (0..32); zero when mode disables blur. */
    val blurRadius: Float,
    /** When true, paint opaque [surfaceContainer] tone instead of tinted glass. */
    val solidSurfaceContainer: Boolean,
    /** True when blurred mode wants frost but host backdrop blur is missing. */
    val frostPlateFallback: Boolean,
)

/**
 * Maps [mode] + stored [panelAlpha] / [panelBlur] to what the panel root should draw.
 *
 * - **transparent** — configurable alpha, blur forced low/zero (clear glass over wallpaper).
 * - **blurred** — configurable alpha + [panelBlur]; frost-plate fallback when host blur is off.
 * - **solid** — opaque surfaceContainer; blur ignored.
 */
object PanelSurfaceMapping {
    const val TRANSPARENT_MAX_BLUR = 0f
    const val MIN_GLASS_ALPHA = 0.05f
    const val MIN_BLURRED_ALPHA = 0.15f
    const val MAX_BLUR = 32f

    fun resolve(
        mode: PanelSurfaceMode,
        panelAlpha: Float,
        panelBlur: Float,
        hostBackdropBlurAvailable: Boolean = PanelSurfaceMode.HOST_BACKDROP_BLUR_AVAILABLE,
    ): PanelSurfaceResolved = when (mode) {
        PanelSurfaceMode.Transparent -> PanelSurfaceResolved(
            mode = mode,
            alpha = panelAlpha.coerceIn(MIN_GLASS_ALPHA, 1f),
            blurRadius = TRANSPARENT_MAX_BLUR,
            solidSurfaceContainer = false,
            frostPlateFallback = false,
        )
        PanelSurfaceMode.Blurred -> {
            val blur = panelBlur.coerceIn(0f, MAX_BLUR)
            PanelSurfaceResolved(
                mode = mode,
                alpha = panelAlpha.coerceIn(MIN_BLURRED_ALPHA, 1f),
                blurRadius = blur,
                solidSurfaceContainer = false,
                frostPlateFallback = blur > 0f && !hostBackdropBlurAvailable,
            )
        }
        PanelSurfaceMode.Solid -> PanelSurfaceResolved(
            mode = mode,
            alpha = 1f,
            blurRadius = 0f,
            solidSurfaceContainer = true,
            frostPlateFallback = false,
        )
    }
}
