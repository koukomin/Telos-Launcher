package de.mm20.launcher2.comms.model

enum class CallType {
    Incoming,
    Outgoing,
    Missed,
    Rejected,
    Blocked,
    Unknown,
}

data class CallLogEntry(
    val id: Long,
    val phoneNumber: String,
    val displayName: String?,
    val type: CallType,
    val timestamp: Long,
    val durationSeconds: Long,
    val photoUri: String? = null,
    val simLabel: String? = null,
    val simAccountId: String? = null,
)
