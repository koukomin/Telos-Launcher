package de.mm20.launcher2.comms.telephony

data class InCallUiState(
    val hasCall: Boolean = false,
    val incoming: Boolean = false,
    val connecting: Boolean = false,
    val active: Boolean = false,
    val onHold: Boolean = false,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val bluetooth: Boolean = false,
    val number: String = "",
    val name: String? = null,
    val photoUri: String? = null,
    val connectedAtEpochMs: Long? = null,
    val secondNumber: String = "",
    val secondName: String? = null,
    val canMerge: Boolean = false,
    val canSwap: Boolean = false,
    val conferenceCount: Int = 0,
)
