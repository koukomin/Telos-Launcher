package com.telos.desktop.ui

/**
 * Telos Helix chrome status. Keep this in sync with
 * `docs/docs/developer-guide/telos-helix.md` so a crashed session can resume.
 *
 * H0–H6 — done
 * Surface S0–S4 — done (icon cards, motion, OSD/toast/clock month,
 * Overview, Control Center)
 *
 * When a later session asks what is still pending for Linux desktop:
 * none of P0–P4, none of Helix H0–H6, none of Surface S0–S4.
 */
object Helix {
    const val PHASE = "S4"
    const val NEXT = "none"
}
