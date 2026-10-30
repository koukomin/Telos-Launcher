package com.telos.desktop.ui

/**
 * Telos Helix chrome status. Keep this in sync with
 * `docs/docs/developer-guide/telos-helix.md` so a crashed session can resume.
 *
 * H0–H5 — done
 * H6 polish — done (opaque context menu, notification drawer, clipboard popup,
 * opaque title bars with window titles, opaque window bodies)
 *
 * When a later session asks what is still pending for Linux desktop:
 * none of P0–P4, none of Helix H0–H6. Do not start extra DEs unless asked.
 */
object Helix {
    const val PHASE = "H6"
    const val NEXT = "none"
}
