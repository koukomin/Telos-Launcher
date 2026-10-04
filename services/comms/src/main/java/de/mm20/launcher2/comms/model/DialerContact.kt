package de.mm20.launcher2.comms.model

data class DialerContact(
    val id: Long,
    val displayName: String,
    val phoneNumbers: List<String>,
    val photoUri: String? = null
)
