package de.mm20.launcher2.comms.model

data class MessageThread(
    val id: Long,
    val address: String,
    val displayName: String?,
    val snippet: String,
    val timestamp: Long,
    val unreadCount: Int,
)
