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

    /**
     * Like [parseDomains] but keeps only the 64 bit hashes (see [DomainSet]) instead of one String per
     * domain: lists with a million entries need ~8 MB instead of 100+ MB of heap.
     */
    fun parseDomainHashes(reader: BufferedReader, maxEntries: Int = 2_000_000): LongArray {
        var out = LongArray(4096)
        var n = 0
        while (n < maxEntries) {
            val line = reader.readLine() ?: break
            val d = parseDomainLine(line) ?: continue
            if (n == out.size) out = out.copyOf(n * 2)
            out[n++] = DomainSet.hash(d)
        }
        return out.copyOf(n)
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

    /** Merged ranges plus the date the list says it was generated on (0 when its header has none) */
    class IpParseResult(val ranges: LongArray, val dataDate: Long) {
        val count: Int get() = ranges.size / 2
    }

    /**
     * IPv4 ranges from PeerGuardian p2p ("name:1.2.3.4-1.2.3.5"), eMule ipfilter.dat
     * ("1.2.3.4 - 1.2.3.5 , 000 , name"), CIDR netsets with comments ("1.2.3.0/24 ; SBL1"),
     * tab separated "start end" (DShield), "a-b" and single addresses. IPv6 lines are skipped.
     * Result: sorted, merged [start, end] pairs as unsigned 32 bit values in a flat array.
     */
    fun parseIpRanges(reader: BufferedReader, maxRanges: Int = 3_000_000): LongArray =
        parseIpRangesInfo(reader, false, maxRanges).ranges

    /**
     * Like [parseIpRanges], and reads the date from the header comments of the list (FireHOL, Spamhaus,
     * abuse.ch, DShield formats). With [skipReserved] private, loopback and link local ranges are cut out,
     * so that lists which contain bogons do not block devices on the local network.
     */
    fun parseIpRangesInfo(reader: BufferedReader, skipReserved: Boolean, maxRanges: Int = 3_000_000): IpParseResult {
        var starts = LongArray(1024)
        var ends = LongArray(1024)
        var n = 0
        var date = 0L
        var lineNo = 0
        while (n < maxRanges) {
            val line = reader.readLine() ?: break
            lineNo++
            if (date == 0L && lineNo <= HEADER_LINES) date = DataDates.parseHeaderLine(line) ?: 0L
            val r = parseIpLine(line) ?: continue
            val parts = if (skipReserved) subtractReserved(r.first, r.second) else listOf(r)
            for (part in parts) {
                if (n == starts.size) {
                    starts = starts.copyOf(n * 2)
                    ends = ends.copyOf(n * 2)
                }
                starts[n] = part.first
                ends[n] = part.second
                n++
            }
        }
        return IpParseResult(mergeRanges(starts, ends, n), date)
    }

    private const val HEADER_LINES = 80

    /** Private, loopback, link local and carrier grade NAT ranges, sorted */
    private val RESERVED = listOf(
        0x0A000000L to 0x0AFFFFFFL, // 10.0.0.0/8
        0x64400000L to 0x647FFFFFL, // 100.64.0.0/10
        0x7F000000L to 0x7FFFFFFFL, // 127.0.0.0/8
        0xA9FE0000L to 0xA9FEFFFFL, // 169.254.0.0/16
        0xAC100000L to 0xAC1FFFFFL, // 172.16.0.0/12
        0xC0A80000L to 0xC0A8FFFFL, // 192.168.0.0/16
    )

    /** [start, end] without the reserved ranges, as zero to several pieces */
    fun subtractReserved(start: Long, end: Long): List<Pair<Long, Long>> {
        var pieces = listOf(start to end)
        for ((rs, re) in RESERVED) {
            val next = ArrayList<Pair<Long, Long>>(pieces.size + 1)
            for ((a, b) in pieces) {
                if (b < rs || a > re) { next.add(a to b); continue }
                if (a < rs) next.add(a to rs - 1)
                if (b > re) next.add(re + 1 to b)
            }
            pieces = next
        }
        return pieces
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
        // trailing comments of netsets: "1.2.3.0/24 ; SBL123" or "1.2.3.4 # note"
        val text = rangeText.substringBefore(';').substringBefore('#').trim()
        if ('\t' in text) {
            // DShield: start <tab> end <tab> ...
            val f = text.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
            val a = f.getOrNull(0)?.let(::parseIpv4)
            val b = f.getOrNull(1)?.let(::parseIpv4)
            if (a != null && b != null) return if (a <= b) a to b else b to a
            return f.getOrNull(0)?.let { first -> parseIpLine(first) }
        }
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
    fun openText(input: InputStream, maxBytes: Long, onGzipDate: ((Long) -> Unit)? = null): BufferedReader {
        val buffered = java.io.BufferedInputStream(input)
        buffered.mark(16)
        val head = ByteArray(8)
        var got = 0
        while (got < head.size) {
            val r = buffered.read(head, got, head.size - got)
            if (r <= 0) break
            got += r
        }
        buffered.reset()
        val b0 = if (got > 0) head[0].toInt() and 0xff else -1
        val b1 = if (got > 1) head[1].toInt() and 0xff else -1
        if (b0 == 0x1f && b1 == 0x8b && got >= 8) onGzipDate?.invoke(gzipDate(head))
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

    /** The modification time in the gzip header (seconds since 1970, little endian at byte 4) in milliseconds, 0 if not set */
    fun gzipDate(head: ByteArray): Long {
        if (head.size < 8) return 0
        val secs = (head[4].toLong() and 0xff) or ((head[5].toLong() and 0xff) shl 8) or
            ((head[6].toLong() and 0xff) shl 16) or ((head[7].toLong() and 0xff) shl 24)
        return secs * 1000
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

/** Reads the "generated on" date of a list from its header comments. */
object DataDates {
    private val label = Regex("""^[#;]\s*(?:this file date|last-modified|last updated|updated)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val formats = listOf(
        "EEE MMM d HH:mm:ss zzz yyyy", // FireHOL: Thu Oct  8 04:41:57 UTC 2026
        "EEE, dd MMM yyyy HH:mm:ss zzz", // Spamhaus: Wed, 07 Oct 2026 17:01:40 GMT
        "yyyy-MM-dd HH:mm:ss zzz", // abuse.ch: 2026-03-04 14:28:39 UTC
        "yyyy-MM-dd'T'HH:mm:ss", // DShield: 2026-10-08T11:15:59.445859
    )

    /** Epoch milliseconds, null when the line is no date comment */
    fun parseHeaderLine(line: String): Long? {
        val m = label.find(line.trim()) ?: return null
        return parse(m.groupValues[1])
    }

    fun parse(value: String): Long? {
        val v = value.trim().replace(Regex("\\s+"), " ").replace(Regex("(\\d{2}:\\d{2}:\\d{2})\\.\\d+"), "$1")
        for (f in formats) {
            val fmt = java.text.SimpleDateFormat(f, java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            fmt.isLenient = false
            val pos = java.text.ParsePosition(0)
            val d = fmt.parse(v, pos)
            if (d != null && pos.index == v.length) return d.time
        }
        return null
    }
}
