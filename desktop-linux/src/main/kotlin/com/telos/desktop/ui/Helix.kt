package com.telos.desktop.ui

/**
 * Telos Helix / Wayland shell status. Keep in sync with
 * `docs/docs/developer-guide/telos-helix.md` and `telos-wayland-shell.md`.
 *
 * H0–H6, Surface S0–S4 — done
 * Wayland W0–W3 — done
 * Next: none of the numbered Wayland shell phases unless the user asks.
 */
object Helix {
    const val PHASE = "W3"
    const val NEXT = "none"
}
