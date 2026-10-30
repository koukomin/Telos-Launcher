package com.telos.desktop.ui

/**
 * Telos Helix chrome status. Keep this in sync with
 * `docs/docs/developer-guide/telos-helix.md` so a crashed session can resume.
 *
 * H0 tokens — done (`TelosSharedColors`)
 * H1 panel glyphs — done (`TelosTopPanel` + `CandyIcon`)
 * H2 Kickoff — done (`PlasmaAppGrid` favorites column)
 * H3 Klassy / dock / workspaces — already in tree
 * H4 Settings visual pass — pending
 */
object Helix {
    const val PHASE = "H3"
    const val NEXT = "H4"
}
