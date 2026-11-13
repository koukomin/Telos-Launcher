package com.telos.desktop.theme

/**
 * Appearance keys that may live in a VOS-1 profile overlay or vault
 * `desktop.properties`. Enum definitions for intensity/accent/density live in
 * [ThemeStudio] (`ThemeIntensity`, `ThemeAccent`, `ThemeDensity`).
 */
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
