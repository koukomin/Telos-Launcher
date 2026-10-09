package de.mm20.launcher2.network.vpn

/** Addresses of the virtual network interface. Apps only ever see these, never the real DNS servers. */
internal object TunnelConstants {
    /** Address of the tunnel interface (IPv4) and its prefix length. */
    const val GATEWAY_V4 = "10.111.222.1"
    const val PREFIX_V4 = 24

    /** The fake DNS server apps are told to use (IPv4). The engine answers there. */
    const val DNS_V4 = "10.111.222.3"

    /** A randomly chosen unique local prefix (RFC 4193) for IPv6. */
    const val GATEWAY_V6 = "fd66:f83a:c650::1"
    const val PREFIX_V6 = 120
    const val DNS_V6 = "fd66:f83a:c650::3"

    /** Interface addresses in the form the engine expects. */
    const val ADDRESSES = "$GATEWAY_V4/$PREFIX_V4,$GATEWAY_V6/$PREFIX_V6"

    /** DNS addresses with port, the way the engine expects them. */
    const val FAKE_DNS = "$DNS_V4:53,[$DNS_V6]:53"

    /** Used by the engine before the network's own DNS servers are known. */
    const val BOOTSTRAP_DNS = "9.9.9.9,149.112.112.112,2620:fe::fe"

    const val DEFAULT_MTU = 1500
    const val MIN_MTU = 1280
}
