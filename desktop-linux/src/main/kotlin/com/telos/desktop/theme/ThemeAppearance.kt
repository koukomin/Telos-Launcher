package com.telos.desktop.theme

import androidx.compose.ui.graphics.Color

/**
 * Vault Neon / Quiet Ops intensity for Helix chrome.
 * Stored as `theme.intensity` (`quiet` | `neon`).
 */
enum class ThemeIntensity(val propertyValue: String) {
    Quiet("quiet"),
    Neon("neon"),
    ;

    companion object {
        fun parse(raw: String?): ThemeIntensity {
            val v = raw?.trim()?.lowercase().orEmpty()
            return when (v) {
                "neon", "vault", "vault_neon", "vault-neon" -> Neon
                else -> Quiet
            }
        }
    }
}

/**
 * Named neon accent presets (Cyber-Tech contract).
 * Stored as `theme.accent` (`green` / `neon-green` / `#RRGGBB`, or `wallpaper`).
 */
enum class ThemeAccent(val propertyValue: String, val color: Color) {
    Green("neon-green", Color(0xFF00E676)),
    Cyan("neon-cyan", Color(0xFF00F0FF)),
    Magenta("neon-magenta", Color(0xFFFF007F)),
    Orange("neon-orange", Color(0xFFFF9100)),
    ;

    companion object {
        fun parse(raw: String?): ThemeAccent {
            val v = raw?.trim()?.lowercase().orEmpty()
            return when {
                v == "wallpaper" -> Magenta
                v == "green" || v == "neon-green" || v == "#00e676" -> Green
                v == "cyan" || v == "neon-cyan" || v == "#00f0ff" -> Cyan
                v == "orange" || v == "neon-orange" || v == "#ff9100" -> Orange
                v == "magenta" || v == "neon-magenta" || v == "#ff007f" || v.isEmpty() -> Magenta
                v.startsWith("#") && v.length == 7 -> {
                    val rgb = v.removePrefix("#").toLongOrNull(16) ?: return Magenta
                    when (rgb) {
                        0x00E676L -> Green
                        0x00F0FFL -> Cyan
                        0xFF9100L -> Orange
                        else -> Magenta
                    }
                }
                else -> Magenta
            }
        }

        /** Preserve `wallpaper` and unknown custom values when writing props. */
        fun canonicalizeForStorage(raw: String): String {
            val v = raw.trim()
            if (v.equals("wallpaper", ignoreCase = true)) return "wallpaper"
            if (v.isEmpty()) return Magenta.propertyValue
            return parse(v).propertyValue
        }
    }
}

/** Appearance keys that may live in a VOS-1 profile overlay. */
object AppearanceKeys {
    const val INTENSITY = "theme.intensity"
    const val ACCENT = "theme.accent"
    const val ICONS_THEME = "icons.theme"
    const val CURSOR_THEME = "cursor.theme"
    const val PANIC_MUTE = "theme.panicMute"

    val PROFILE_OVERLAY_KEYS: Set<String> = setOf(
        INTENSITY,
        ACCENT,
        ICONS_THEME,
        CURSOR_THEME,
    )
}
