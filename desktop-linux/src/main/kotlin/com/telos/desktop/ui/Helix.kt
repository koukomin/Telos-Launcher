package com.telos.desktop.ui

/**
 * Telos Helix chrome status. Keep this in sync with
 * `docs/docs/developer-guide/telos-helix.md` so a crashed session can resume.
 *
 * H0 tokens — done
 * H1 panel glyphs — done
 * H2 Kickoff — done
 * H3 Klassy / workspaces — done
 * H4 Settings cards — done
 * H5 Dock + Alt-Tab — done (icon dock, Kickoff from dock, opaque Alt-Tab)
 *
 * When a later session asks what is still pending for Linux desktop:
 * none of P0–P4, none of Helix H0–H5. Do not start extra DEs unless asked.
 */
object Helix {
    const val PHASE = "H5"
    const val NEXT = "none"
}
