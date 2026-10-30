package com.telos.desktop.ui

/**
 * Telos Helix chrome status. Keep this in sync with
 * `docs/docs/developer-guide/telos-helix.md` so a crashed session can resume.
 *
 * H0 tokens — done (`TelosSharedColors`)
 * H1 panel glyphs — done (`TelosTopPanel` + `CandyIcon`)
 * H2 Kickoff — done (`PlasmaAppGrid` favorites column)
 * H3 Klassy / dock / workspaces — already in tree
 * H4 Settings visual pass — done (`LinuxDesktopSettingsPanel` grouped cards)
 *
 * When a later session asks what is still pending for Linux desktop:
 * none of P0–P4, none of Helix H0–H4. Do not start extra DEs unless asked.
 */
object Helix {
    const val PHASE = "H4"
    const val NEXT = "none"
}
