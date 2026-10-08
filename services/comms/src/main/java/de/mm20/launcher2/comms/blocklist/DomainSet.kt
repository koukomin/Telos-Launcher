package de.mm20.launcher2.comms.blocklist

/**
 * Compact domain lookup: 64 bit hashes of the domains in a sorted array (8 bytes per entry
 * instead of a String object). A hash collision would need ~10^9 entries to become likely.
 */
class DomainSet(private val hashes: LongArray) {
    val size: Int get() = hashes.size

    /** True if [host] or one of its parent domains is in the set. */
    fun matches(host: String): Boolean {
        if (hashes.isEmpty()) return false
        val h = host.lowercase().trimEnd('.')
        var start = 0
        while (true) {
            if (contains(hash(h, start, h.length))) return true
            val dot = h.indexOf('.', start)
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false // never match a bare TLD
            start = dot + 1
        }
    }

    fun contains(hash: Long): Boolean = java.util.Arrays.binarySearch(hashes, hash) >= 0

    fun toArray(): LongArray = hashes

    companion object {
        val EMPTY = DomainSet(LongArray(0))

        fun hash(s: String, from: Int = 0, to: Int = s.length): Long {
            var h = -0x340d631b7bdddcdbL // FNV offset basis
            for (i in from until to) {
                h = (h xor s[i].code.toLong()) * 0x100000001b3L
            }
            // final avalanche
            h = (h xor (h ushr 33)) * -0xae502812aa7333L
            return h xor (h ushr 29)
        }

        fun of(domains: Collection<String>): DomainSet {
            val a = LongArray(domains.size)
            var i = 0
            for (d in domains) a[i++] = hash(d)
            return fromHashes(a)
        }

        fun fromHashes(a: LongArray): DomainSet {
            a.sort()
            var m = 0
            for (i in a.indices) if (i == 0 || a[i] != a[i - 1]) a[m++] = a[i]
            return DomainSet(a.copyOf(m))
        }

        fun merge(sets: List<DomainSet>): DomainSet {
            val all = LongArray(sets.sumOf { it.size })
            var p = 0
            for (s in sets) { System.arraycopy(s.hashes, 0, all, p, s.size); p += s.size }
            return fromHashes(all)
        }
    }
}
