package de.mm20.launcher2.comms.telephony

/** Telecom callback state, not a possibly older Call.Details snapshot. */
internal fun callPriority(state: Int): Int = when (state) {
    2, 13 -> 0 // ringing
    4 -> 1 // active
    1, 8, 9, 11, 12 -> 2 // dialing, connecting, account selection, audio processing
    3 -> 3 // holding
    7, 10 -> Int.MAX_VALUE // disconnected, disconnecting
    else -> 4
}
