package de.mm20.launcher2.network.nextdns

/** Public state never contains the API credential. */
data class NextDnsCredentialsInfo(val profileId: String?, val hasKey: Boolean)
data class NextDnsProfile(val id: String, val name: String)
data class NextDnsLog(val timestamp: String, val domain: String, val status: String,
    val deviceName: String? = null, val reasons: List<String> = emptyList(), val protocol: String? = null)
data class NextDnsRule(val id: String, val active: Boolean)
data class NextDnsPage<T>(val items: List<T>, val cursor: String?)
enum class NextDnsErrorKind { AUTH, INVALID, RATE_LIMIT, NETWORK, API, UNCONFIGURED }
/** Do not propagate server error bodies, keys, query domains or URLs into crash reports. */
class NextDnsException(val kind: NextDnsErrorKind) : Exception("NextDNS ${kind.name}")
