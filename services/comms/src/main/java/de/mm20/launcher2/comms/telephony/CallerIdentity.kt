package de.mm20.launcher2.comms.telephony

data class CallerIdentity(
    val name: String? = null,
    val photoUri: String? = null,
    val localContactFound: Boolean = false,
    val localLookupComplete: Boolean = false,
    val suggestedName: String? = null,
    val likelySpam: Boolean = false,
    val sourceUrl: String? = null,
)

/** Prefer the user's address book over carrier text; never show an empty caller label. */
internal fun callerName(local: String?, carrier: String?, remote: String?): String? =
    sequenceOf(local, carrier, remote).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull()

internal fun callActionAllowed(state: InCallUiState, token: Long, action: String?): Boolean =
    state.hasCall && token != 0L && state.actionToken == token && when (action) {
        "answer", "reject" -> state.incoming
        "hangup" -> !state.incoming
        else -> false
    }

/** Failed/no-permission local lookups do not establish that a number is unknown. */
internal fun shouldLookupCaller(identity: CallerIdentity, remoteName: String?, hidden: Boolean): Boolean =
    identity.localLookupComplete && !identity.localContactFound && remoteName.isNullOrBlank() && !hidden
