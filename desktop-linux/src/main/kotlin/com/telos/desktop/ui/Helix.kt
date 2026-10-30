package com.telos.desktop.ui

/**
 * Telos Helix / Wayland shell status. Keep in sync with
 * `docs/docs/developer-guide/telos-helix.md` and `telos-wayland-shell.md`.
 *
 * H0–H6, Surface S0–S4 — done
 * Wayland W0 — done
 * Wayland W1 — done (GtkLayerShell panel/dock + socket; Compose fallback)
 * W2 portals/SNI — pending until asked
 */
object Helix {
    const val PHASE = "W1"
    const val NEXT = "W2"
}
