package de.mm20.launcher2.downloads.logic

import de.mm20.launcher2.downloads.TorrentFile
import java.net.URLDecoder

/** What a pasted or shared text is for the torrent engine */
enum class TorrentSourceKind { Magnet, TorrentUrl, TorrentFile, None }

object TorrentSources {
    private val magnetRegex = Regex("""magnet:\?[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private val urlRegex = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)

    fun classify(text: String): TorrentSourceKind {
        val t = text.trim()
        val lower = t.lowercase()
        return when {
            lower.startsWith("magnet:?") && lower.contains("xt=urn:bt") -> TorrentSourceKind.Magnet
            (lower.startsWith("http://") || lower.startsWith("https://")) && isTorrentPath(lower) -> TorrentSourceKind.TorrentUrl
            (lower.startsWith("file:") || lower.startsWith("content:")) -> TorrentSourceKind.TorrentFile
            else -> TorrentSourceKind.None
        }
    }

    private fun isTorrentPath(lowerUrl: String) = lowerUrl.substringBefore('#').substringBefore('?').endsWith(".torrent")

    /** Magnet links and .torrent addresses in [text], in order, without duplicates */
    fun extract(text: String): List<String> {
        val out = LinkedHashSet<String>()
        for (m in magnetRegex.findAll(text)) {
            val link = m.value.trimEnd('.', ',', ';', ')', ']', '}', '!')
            if (classify(link) == TorrentSourceKind.Magnet) out.add(link)
        }
        for (m in urlRegex.findAll(text)) {
            val link = m.value.trimEnd('.', ',', ';', ')', ']', '}', '!', '?')
            if (classify(link) == TorrentSourceKind.TorrentUrl) out.add(link)
        }
        return out.toList()
    }

    fun containsTorrent(text: String) = extract(text).isNotEmpty()

    /** The info hash (hex, lower case; base32 hashes are converted) of a magnet link, or null */
    fun magnetInfoHash(magnet: String): String? {
        val xt = queryValues(magnet, "xt").firstOrNull { it.lowercase().startsWith("urn:btih:") } ?: return null
        val h = xt.substring("urn:btih:".length)
        return when {
            h.length == 40 && h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> h.lowercase()
            h.length == 32 -> base32ToHex(h)
            else -> null
        }
    }

    /** The "dn" (display name) of a magnet link */
    fun magnetName(magnet: String): String? = queryValues(magnet, "dn").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    fun magnetTrackers(magnet: String): List<String> = queryValues(magnet, "tr")

    private fun queryValues(magnet: String, key: String): List<String> {
        val q = magnet.substringAfter('?', "")
        return q.split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0 || !part.substring(0, i).equals(key, true)) null
            else try { URLDecoder.decode(part.substring(i + 1), "UTF-8") } catch (e: Exception) { null }
        }
    }

    private fun base32ToHex(s: String): String? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0
        var value = 0
        val sb = StringBuilder()
        for (c in s.uppercase()) {
            val v = alphabet.indexOf(c)
            if (v < 0) return null
            value = (value shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                sb.append(String.format("%02x", (value shr bits) and 0xff))
            }
        }
        return if (sb.length == 40) sb.toString() else null
    }
}

/** Which files of a torrent are downloaded, and how important they are */
object FileSelection {
    const val SKIP = 0
    const val NORMAL = 4
    const val MAX = 7

    /** Priorities for libtorrent, one per file index; unknown indexes are skipped */
    fun priorities(files: List<TorrentFile>): IntArray {
        val size = (files.maxOfOrNull { it.index } ?: -1) + 1
        val out = IntArray(size) { SKIP }
        for (f in files) if (f.index >= 0) out[f.index] = f.priority.coerceIn(SKIP, MAX)
        return out
    }

    /** Only the files in [selected] are wanted, with priority [priority]; the others are skipped */
    fun select(files: List<TorrentFile>, selected: Set<Int>, priority: Int = NORMAL): List<TorrentFile> =
        files.map { it.copy(priority = if (it.index in selected) priority.coerceIn(1, MAX) else SKIP) }

    /** Applies new priorities (file index to priority) and leaves the other files as they are */
    fun apply(files: List<TorrentFile>, changes: Map<Int, Int>): List<TorrentFile> =
        files.map { f -> changes[f.index]?.let { f.copy(priority = it.coerceIn(SKIP, MAX)) } ?: f }

    fun selectedBytes(files: List<TorrentFile>): Long = files.filter { it.wanted }.sumOf { it.size }

    fun selectedCount(files: List<TorrentFile>): Int = files.count { it.wanted }

    /** A torrent needs at least one wanted file to do anything */
    fun isValid(files: List<TorrentFile>): Boolean = files.isEmpty() || files.any { it.wanted }

    /** Bytes of the wanted files that are still missing */
    fun remainingBytes(files: List<TorrentFile>): Long = files.filter { it.wanted }.sumOf { (it.size - it.done).coerceAtLeast(0) }
}

/** When seeding ends. Pure, see TorrentLogicTest. */
object SeedRules {
    /**
     * @param ratioLimitX100 0: no ratio limit
     * @param minutesLimit 0: no time limit
     * With neither limit and without [stopAtDone] a torrent seeds until the user stops it.
     */
    fun shouldStop(stopAtDone: Boolean, ratioLimitX100: Int, minutesLimit: Int, ratio: Double, seedingSeconds: Long): Boolean {
        if (stopAtDone) return true
        if (ratioLimitX100 > 0 && ratio >= ratioLimitX100 / 100.0) return true
        if (minutesLimit > 0 && seedingSeconds >= minutesLimit * 60L) return true
        return false
    }

    /** Uploaded divided by downloaded; for a torrent that was already complete when it was added, divided by its size */
    fun ratio(uploaded: Long, received: Long, size: Long): Double {
        val base = if (received > 0) received else size
        return if (base > 0) uploaded.toDouble() / base else 0.0
    }

    /** "1.25" */
    fun formatRatio(ratio: Double): String = String.format(java.util.Locale.ROOT, "%.2f", ratio)
}

/** The piece map: many pieces drawn as a few cells */
object PieceMap {
    /**
     * Fraction of finished pieces in each of [cells] cells (0..1). Fewer pieces than cells: one cell per piece.
     */
    fun cells(numPieces: Int, cells: Int, have: (Int) -> Boolean): FloatArray {
        if (numPieces <= 0 || cells <= 0) return FloatArray(0)
        val n = minOf(cells, numPieces)
        val out = FloatArray(n)
        for (c in 0 until n) {
            val from = (c.toLong() * numPieces / n).toInt()
            val to = ((c + 1).toLong() * numPieces / n).toInt().coerceAtLeast(from + 1)
            var got = 0
            for (p in from until to) if (have(p)) got++
            out[c] = got.toFloat() / (to - from)
        }
        return out
    }
}

/** Letters for the connection of a peer, like in other torrent clients */
object PeerFlagText {
    fun describe(
        interesting: Boolean, remoteChoked: Boolean, remoteInterested: Boolean, choked: Boolean,
        optimistic: Boolean, snubbed: Boolean, incoming: Boolean, encrypted: Boolean, utp: Boolean,
    ): String = buildString {
        // D: we download from it, d: we want to but it chokes us; U: we upload to it, u: it wants to but we choke it
        if (interesting) append(if (remoteChoked) 'd' else 'D')
        if (remoteInterested) append(if (choked) 'u' else 'U')
        if (optimistic) append('O')
        if (snubbed) append('S')
        append(if (incoming) 'I' else 'C')
        if (encrypted) append('E')
        if (utp) append('P')
    }
}

/** Folder and file names from a torrent are not trusted */
object TorrentPaths {
    /** The path split into safe names: no empty parts, no "." or "..", no characters that file systems refuse */
    fun safeSegments(path: String): List<String> =
        path.replace('\\', '/').split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .map { FileNames.sanitize(it).ifBlank { "_" } }

    /** Top folder name of a multi-file torrent ("" when the files are not in one folder) */
    fun rootFolder(paths: List<String>): String {
        if (paths.size < 2) return ""
        val firsts = paths.map { safeSegments(it).firstOrNull() }
        val first = firsts.first() ?: return ""
        return if (firsts.all { it == first } && paths.all { safeSegments(it).size > 1 }) first else ""
    }
}
