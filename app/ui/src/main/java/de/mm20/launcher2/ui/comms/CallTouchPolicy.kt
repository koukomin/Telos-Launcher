package de.mm20.launcher2.ui.comms

/** Pocket filtering must never swallow the explicit call-termination gesture. */
internal fun blockPocketTouch(pocket: Boolean, near: Boolean, incoming: Boolean, termination: Boolean): Boolean =
    pocket && near && incoming && !termination
