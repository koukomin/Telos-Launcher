package com.telos.desktop.ui

/**
 * Telos Helix / Wayland shell status. Keep in sync with
 * `docs/docs/developer-guide/telos-helix.md` and `telos-wayland-shell.md`.
 *
 * H0–H6, Surface S0–S4 — done
 * Wayland W0 — done (struts, notifyd, portals.conf, PAM lock)
 * W1 native layer-shell — pending until asked
 */
object Helix {
    const val PHASE = "W0"
    const val NEXT = "W1"
}
