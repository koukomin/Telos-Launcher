package com.telos.desktop.wm

import com.telos.desktop.session.SessionCompositor

/**
 * Helix windowing preference persisted as `desktop.windowMode` in vault
 * `desktop.properties`.
 *
 * - [Stacking] → labwc (floating / stacking compositor)
 * - [Tiling] → niri (tiling compositor)
 *
 * Preference applies on the next `telos-session` start when `TELOS_COMPOSITOR`
 * is unset. Explicit session desktop files (`telos-niri.desktop`) still win.
 */
enum class WindowMode(val propertyValue: String, val compositor: String) {
    Stacking("stacking", SessionCompositor.Labwc),
    Tiling("tiling", SessionCompositor.Niri);

    companion object {
        fun parse(raw: String?): WindowMode {
            val v = raw?.trim()?.lowercase().orEmpty()
            return when (v) {
                Tiling.propertyValue, SessionCompositor.Niri -> Tiling
                Stacking.propertyValue, SessionCompositor.Labwc, "" -> Stacking
                else -> Stacking
            }
        }
    }
}
