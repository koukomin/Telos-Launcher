package de.mm20.launcher2.network.tor

import de.mm20.launcher2.network.util.IpUtil

/** No DNS calls here: this policy also runs on firestack's callback threads. */
internal object TorPolicy {
    const val PROXY = "telos-tor"
    const val DNS = "telos-tor-dns"
    const val VIRTUAL_V4 = "10.192.0.0/10"
    const val VIRTUAL_V6 = "fd87:d87e:eb43::/48"
    private val ranges = listOfNotNull(IpUtil.parseCidr(VIRTUAL_V4), IpUtil.parseCidr(VIRTUAL_V6))

    fun isOnion(domain: String?): Boolean {
        val name = domain.orEmpty().trimEnd('.').lowercase()
        return name == "onion" || name.endsWith(".onion")
    }

    fun isVirtual(ip: String): Boolean = IpUtil.parse(ip)?.let { address -> ranges.any { it.contains(address) } } == true

    fun required(uid: Int, domain: String?, ips: List<String>, enabled: Boolean, appIds: Set<Int>): Boolean =
        isOnion(domain) || ips.any(::isVirtual) ||
            (enabled && appIds.isNotEmpty() && (uid < 0 || uid % 100000 in appIds))

    /** Tor has no UDP transport. Never fall back when a circuit is not ready. */
    fun mayConnect(enabled: Boolean, ready: Boolean, protocol: Int): Boolean = enabled && ready && protocol == 6
}
