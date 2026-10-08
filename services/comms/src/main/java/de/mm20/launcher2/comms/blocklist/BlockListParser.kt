package de.mm20.launcher2.comms.blocklist

import java.io.BufferedReader
import java.io.InputStream

/**
 * Tolerant parsers for block list formats. Every parser skips what it does not understand
 * instead of failing, so one odd line never discards a whole list.
 */
object BlockListParser {

    /** Hosts file, plain domain list, wildcard domain list and the domain subset of adblock rules. */
    fun parseDomains(reader: BufferedReader, maxEntries: Int = 2_000_000): Set<String> {
        val out = HashSet<String>()
        while (out.size < maxEntries) {
            val line = reader.readLine() ?: break
            parseDomainLine(line)?.let { out.add(it) }
        }
        return out
    }

    fun parseDomainLine(raw: String): String? {
        var line = raw.trim().trimStart('﻿')
        if (line.isEmpty()) return null
        when (line[0]) {
            '#', '!', '[', ';' -> return null
        }
        if (line.startsWith("@@") || line.contains("##") || line.contains("#@#") || line.contains("#?#")) return null

        if (line.startsWith("||")) {
            // adblock network rule: ||domain^ with at most the $third-party option
            line = line.substring(2)
            val caret = line.indexOf('^')
            if (caret < 0) return null
            val rest = line.substring(caret + 1)
            if (rest.isNotEmpty()) {
                val options = rest.removePrefix("$").split(',').map { it.trim() }
                if (!rest.startsWith("$") || options.any { it != "third-party" && it != "3p" && it != "all" }) return null
            }
            return normalizeDomain(line.substring(0, caret))
        }
        if (line[0] == '|' || line[0] == '/' || line[0] == '@') return null

        val hash = line.indexOf('#')
        if (hash >= 0) line = line.substring(0, hash).trim()
        val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
        return when (parts.size) {
            1 -> normalizeDomain(parts[0].removePrefix("*."))
            // hosts format: "0.0.0.0 domain"; only blocking addresses count
            2 -> if (parts[0] in HOSTS_SINKS) normalizeDomain(parts[1]) else null
            else -> null
        }
    }

    private val HOSTS_SINKS = setOf("0.0.0.0", "127.0.0.1", "::", "::1", "0")
    private val IGNORED = setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost", "ip6-loopback", "0.0.0.0")
    private val RANGE_CHARS = Regex("^[0-9.\\s/-]+$")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun normalizeDomain(input: String): String? {
        val d = input.trim().trimEnd('.').lowercase()
        if (d.length < 3 || d.length > 253 || '.' !in d) return null
        if (d in IGNORED || IPV4.matches(d)) return null
        for (c in d) {
            if (!(c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_')) return null
        }
        if (d.startsWith('.') || d.contains("..")) return null
        return d
    }

    /**
     * IPv4 ranges from PeerGuardian p2p ("name:1.2.3.4-1.2.3.5"), eMule ipfilter.dat
     * ("1.2.3.4 - 1.2.3.5 , 000 , name"), CIDR ("1.2.3.0/24"), "a-b" and single addresses.
     * Result: sorted, merged [start, end] pairs as unsigned 32 bit values in a flat array.
     */
    fun parseIpRanges(reader: BufferedReader, maxRanges: Int = 3_000_000): LongArray {
        var starts = LongArray(1024)
        var ends = LongArray(1024)
        var n = 0
        while (n < maxRanges) {
            val line = reader.readLine() ?: break
            val r = parseIpLine(line) ?: continue
            if (n == starts.size) {
                starts = starts.copyOf(n * 2)
                ends = ends.copyOf(n * 2)
            }
            starts[n] = r.first
            ends[n] = r.second
            n++
        }
        return mergeRanges(starts, ends, n)
    }

    fun mergeRanges(starts: LongArray, ends: LongArray, n: Int): LongArray {
        val idx = (0 until n).sortedWith(compareBy({ starts[it] }, { ends[it] }))
        val out = LongArray(n * 2)
        var m = 0
        for (i in idx) {
            if (m > 0 && starts[i] <= out[m - 1] + 1) {
                if (ends[i] > out[m - 1]) out[m - 1] = ends[i]
            } else {
                out[m++] = starts[i]
                out[m++] = ends[i]
            }
        }
        return out.copyOf(m)
    }

    fun parseIpLine(raw: String): Pair<Long, Long>? {
        val line = raw.trim().trimStart('﻿')
        if (line.isEmpty() || line[0] == '#' || line[0] == ';' || line.startsWith("//")) return null
        // p2p: the name may contain ':' and '-', the range follows the last ':'
        val colon = line.lastIndexOf(':')
        val afterColon = if (colon >= 0) line.substring(colon + 1) else ""
        val rangeText = if (colon >= 0 && '.' in afterColon && RANGE_CHARS.matches(afterColon)) {
            afterColon
        } else if (',' in line) {
            // ipfilter.dat: range, level, name
            line.substringBefore(',')
        } else line
        val text = rangeText.trim()
        if (text.contains('/')) {
            val ip = parseIpv4(text.substringBefore('/').trim()) ?: return null
            val bits = text.substringAfter('/').trim().toIntOrNull() ?: return null
            if (bits !in 0..32) return null
            val mask = if (bits == 0) 0L else (0xFFFFFFFFL shl (32 - bits)) and 0xFFFFFFFFL
            val start = ip and mask
            return start to (start or (mask.inv() and 0xFFFFFFFFL))
        }
        val dash = text.indexOf('-')
        if (dash > 0) {
            val a = parseIpv4(text.substring(0, dash).trim()) ?: return null
            val b = parseIpv4(text.substring(dash + 1).trim()) ?: return null
            return if (a <= b) a to b else b to a
        }
        val single = parseIpv4(text) ?: return null
        return single to single
    }

    /** Dotted quad (leading zeros allowed, as written by ipfilter.dat) to an unsigned value. */
    fun parseIpv4(s: String): Long? {
        val parts = s.split('.')
        if (parts.size != 4) return null
        var v = 0L
        for (p in parts) {
            if (p.isEmpty() || p.length > 3) return null
            val o = p.toIntOrNull() ?: return null
            if (o !in 0..255) return null
            v = (v shl 8) or o.toLong()
        }
        return v
    }

    fun formatIpv4(v: Long): String =
        "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"

    /**
     * Opens a downloaded or imported stream as text: gzip and zip (first file) are unpacked,
     * anything else is read as it is. Decompressed output is capped at [maxBytes].
     */
    fun openText(input: InputStream, maxBytes: Long): BufferedReader {
        val buffered = java.io.BufferedInputStream(input)
        buffered.mark(4)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()
        val stream: InputStream = when {
            b0 == 0x1f && b1 == 0x8b -> java.util.zip.GZIPInputStream(buffered)
            b0 == 'P'.code && b1 == 'K'.code -> java.util.zip.ZipInputStream(buffered).also { zip ->
                var entry = zip.nextEntry
                while (entry != null && entry.isDirectory) entry = zip.nextEntry
                if (entry == null) throw java.io.IOException("Empty archive")
            }
            else -> buffered
        }
        return LimitedInputStream(stream, maxBytes).bufferedReader(Charsets.UTF_8)
    }

    private class LimitedInputStream(private val inner: InputStream, private var left: Long) : InputStream() {
        override fun read(): Int {
            if (left <= 0) throw java.io.IOException("List is too large")
            val r = inner.read()
            if (r >= 0) left--
            return r
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) throw java.io.IOException("List is too large")
            val r = inner.read(b, off, minOf(len.toLong(), left).toInt())
            if (r > 0) left -= r
            return r
        }

        override fun close() = inner.close()
    }
}
