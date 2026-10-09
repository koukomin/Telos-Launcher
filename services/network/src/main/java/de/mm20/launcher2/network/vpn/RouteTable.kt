package de.mm20.launcher2.network.vpn

/** Computes the routes of the tunnel when the local network is to be left out of it. */
internal object RouteTable {
    /** Private, loopback, link-local and multicast IPv4 ranges: start address, prefix length. */
    private val ipv4Lan: List<Pair<Long, Int>> = listOf(
        ip4(127, 0, 0, 0) to 8,
        ip4(10, 0, 0, 0) to 8,
        ip4(172, 16, 0, 0) to 12,
        ip4(192, 168, 0, 0) to 16,
        ip4(169, 254, 0, 0) to 16,
        ip4(224, 0, 0, 0) to 3,
    )

    private fun ip4(a: Int, b: Int, c: Int, d: Int): Long =
        (a.toLong() shl 24) or (b.toLong() shl 16) or (c.toLong() shl 8) or d.toLong()

    private fun format(address: Long): String =
        "${(address shr 24) and 0xff}.${(address shr 16) and 0xff}.${(address shr 8) and 0xff}.${address and 0xff}"

    /** All IPv4 CIDR routes that cover everything except the LAN ranges. */
    fun ipv4WithoutLan(): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        var cursor = 0L
        val max = 0xFFFFFFFFL
        for ((start, prefix) in ipv4Lan.sortedBy { it.first }) {
            val end = start + (1L shl (32 - prefix)) - 1
            if (start > cursor) addRange(result, cursor, start - 1)
            cursor = end + 1
        }
        if (cursor <= max) addRange(result, cursor, max)
        return result
    }

    private fun addRange(out: MutableList<Pair<String, Int>>, from: Long, to: Long) {
        var start = from
        while (start <= to) {
            // the largest block that starts at `start`, is aligned, and does not run past `to`
            var size = if (start == 0L) 1L shl 32 else java.lang.Long.lowestOneBit(start)
            while (start + size - 1 > to) size = size shr 1
            val prefix = 32 - java.lang.Long.numberOfTrailingZeros(size)
            out.add(format(start) to prefix)
            start += size
        }
    }

    /** IPv6 global unicast and the NAT64 prefixes, without link-local, unique-local and multicast. */
    val ipv6WithoutLan: List<Pair<String, Int>> = listOf(
        "0000::" to 64,
        "2000::" to 3,
        "4000::" to 3,
        "6000::" to 3,
        "8000::" to 3,
        "a000::" to 3,
        "c000::" to 3,
        "e000::" to 4,
        "f000::" to 5,
        "64:ff9b:1::" to 48,
        "64:ff9b::" to 96,
    )
}
