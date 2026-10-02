package de.mm20.launcher2.comms.model

/**
 * A contact as the dialer/T9 engine needs it: just enough to match and call, independent of
 * `:core:base`'s `search.Contact` (a `SavableSearchable` with icon/launch/serializer semantics
 * built for universal search-result rendering, not a great fit for a dialer's own contact list).
 */
data class DialerContact(
    val id: Long,
    val displayName: String,
    val phoneNumbers: List<String>,
)
