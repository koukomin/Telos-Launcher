package de.mm20.launcher2.comms.media.video

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.math.ln

/** Language codes: the app uses two letters, the older OpenSubtitles API three. */
object SubtitleLanguages {
    // the ISO 639-2/B codes that differ from Java's /T codes, as OpenSubtitles uses them
    private val bibliographic = mapOf(
        "sq" to "alb", "hy" to "arm", "eu" to "baq", "my" to "bur", "zh" to "chi", "cs" to "cze",
        "nl" to "dut", "fr" to "fre", "ka" to "geo", "de" to "ger", "el" to "ell", "is" to "ice",
        "mk" to "mac", "ms" to "may", "fa" to "per", "ro" to "rum", "sk" to "slo", "cy" to "wel",
    )

    fun toOpenSubtitles(code: String): String? {
        val two = code.trim().lowercase().substringBefore('-').substringBefore('_')
        if (two.length != 2) return null
        bibliographic[two]?.let { return it }
        return runCatching { Locale(two).isO3Language }.getOrNull()?.takeIf { it.length == 3 }
    }

    /** "el, EN,pt-BR" -> [el, en, pt] */
    fun parseList(setting: String): List<String> =
        setting.split(',', ';', ' ').map { it.trim().lowercase().substringBefore('-').substringBefore('_') }
            .filter { it.length == 2 }.distinct().ifEmpty { listOf("en") }
}

/** Orders search results: language order first, then hash match, release similarity, popularity. */
object SubtitleRanking {
    private fun tokens(s: String): Set<String> =
        s.lowercase().replace(Regex("\\.(srt|ass|ssa|vtt|sub|mkv|mp4|avi)$"), "")
            .split(Regex("[^a-z0-9]+")).filter { it.length > 1 }.toSet()

    /** 0..1: how much of the video's file name appears in the release name */
    fun similarity(release: String, videoFileName: String): Float {
        val a = tokens(release)
        val b = tokens(videoFileName)
        if (a.isEmpty() || b.isEmpty()) return 0f
        return a.intersect(b).size.toFloat() / b.size
    }

    fun score(r: SubtitleResult, languages: List<String>, videoFileName: String): Float {
        val idx = languages.indexOf(r.language.lowercase().take(2))
        val lang = if (idx >= 0) (languages.size - idx) * 1000f else 0f
        val hash = if (r.hashMatch) 500f else 0f
        val sim = maxOf(similarity(r.release, videoFileName), similarity(r.fileName, videoFileName)) * 300f
        val pop = minOf(ln(r.downloads.toFloat() + 1f) * 6f, 60f)
        val hi = if (r.hearingImpaired) -80f else 0f
        return lang + hash + sim + pop + hi
    }

    fun rank(results: List<SubtitleResult>, languages: List<String>, videoFileName: String): List<SubtitleResult> =
        results.sortedByDescending { score(it, languages, videoFileName) }
}

/** The OpenSubtitles file hash: size plus the 64 bit words of the first and last 64 KiB. */
object OpenSubtitlesHash {
    private const val CHUNK = 65536

    /** [readAt] fills the array from the given offset and returns how many bytes it read */
    fun compute(size: Long, readAt: (offset: Long, buffer: ByteArray) -> Int): String? {
        if (size < CHUNK) return null
        var hash = size
        for (offset in longArrayOf(0L, size - CHUNK)) {
            val buf = ByteArray(CHUNK)
            var read = 0
            while (read < CHUNK) {
                val tmp = ByteArray(CHUNK - read)
                val got = readAt(offset + read, tmp)
                if (got <= 0) return null
                System.arraycopy(tmp, 0, buf, read, got)
                read += got
            }
            val bb = ByteBuffer.wrap(buf).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            while (bb.remaining() >= 8) hash += bb.long
        }
        return "%016x".format(hash)
    }
}

/** Unpacks zip and gzip downloads. */
object SubtitleArchive {
    val EXTENSIONS = listOf("srt", "ass", "ssa", "vtt", "sub", "ttml")
    private const val LIMIT = 6_000_000

    /** The subtitle file name inside (if the data was zipped) and its bytes */
    fun extract(data: ByteArray): Pair<String?, ByteArray> {
        if (data.size > 4 && data[0] == 0x50.toByte() && data[1] == 0x4B.toByte()) {
            val candidates = mutableListOf<Pair<String, ByteArray>>()
            ZipInputStream(ByteArrayInputStream(data)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val ext = e.name.substringAfterLast('.', "").lowercase()
                    if (e.isDirectory || ext !in EXTENSIONS) continue
                    candidates += e.name to readLimited(zip)
                }
            }
            val best = EXTENSIONS.firstNotNullOfOrNull { ext -> candidates.firstOrNull { it.first.lowercase().endsWith(".$ext") } }
            if (best != null) return best.first.substringAfterLast('/') to best.second
            error("No subtitle in the downloaded archive")
        }
        if (data.size > 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
            return null to GZIPInputStream(ByteArrayInputStream(data)).use { readLimited(it) }
        }
        return null to data
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > LIMIT) error("The subtitle file is too big")
        }
        return out.toByteArray()
    }
}

/** Turns the bytes of a subtitle file into text, whatever its character set. */
object SubtitleText {
    private val byLanguage = mapOf(
        "el" to "windows-1253", "ru" to "windows-1251", "bg" to "windows-1251", "uk" to "windows-1251",
        "sr" to "windows-1251", "mk" to "windows-1251", "be" to "windows-1251",
        "cs" to "windows-1250", "pl" to "windows-1250", "hu" to "windows-1250", "ro" to "windows-1250",
        "sk" to "windows-1250", "hr" to "windows-1250", "sl" to "windows-1250", "sq" to "windows-1250",
        "tr" to "windows-1254", "he" to "windows-1255", "ar" to "windows-1256", "fa" to "windows-1256",
        "ja" to "Shift_JIS", "ko" to "EUC-KR", "zh" to "GBK", "th" to "windows-874", "vi" to "windows-1258",
        "lt" to "windows-1257", "lv" to "windows-1257", "et" to "windows-1257",
    )

    private fun strict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        null
    }

    private fun charsetOrNull(name: String?): Charset? = runCatching { Charset.forName(name ?: return null) }.getOrNull()

    fun decode(bytes: ByteArray, languageHint: String?, declared: String?): String {
        val text = decodeRaw(bytes, languageHint, declared)
        return text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun decodeRaw(bytes: ByteArray, languageHint: String?, declared: String?): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            // UTF-16 without a BOM: every second byte is zero for latin text
            val sample = minOf(bytes.size, 400)
            var even = 0
            var odd = 0
            for (i in 0 until sample) if (bytes[i] == 0.toByte()) { if (i % 2 == 0) even++ else odd++ }
            if (odd > sample / 5 && even == 0) return String(bytes, Charsets.UTF_16LE)
            if (even > sample / 5 && odd == 0) return String(bytes, Charsets.UTF_16BE)
        }
        strict(bytes, Charsets.UTF_8)?.let { return it }
        val lang = languageHint?.lowercase()?.take(2)
        val candidates = listOfNotNull(
            charsetOrNull(declared)?.takeIf { it != Charsets.UTF_8 },
            charsetOrNull(byLanguage[lang]),
        )
        for (c in candidates) strict(bytes, c)?.let { return it }
        candidates.firstOrNull()?.let { return String(bytes, it) }
        return String(bytes, charsetOrNull("windows-1252") ?: Charsets.ISO_8859_1)
    }
}

/** Moves every cue of an SRT, WebVTT or ASS/SSA file in time (the subtitle delay). */
object SubtitleShift {
    private val cue = Regex("(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})([.,])(\\d{3})")
    private val arrow = Regex("^(.*?)-->(.*)$")
    private val ass = Regex("^((?:Dialogue|Comment):\\s*[^,]*,)(\\d+:\\d{2}:\\d{2}\\.\\d{2}),(\\d+:\\d{2}:\\d{2}\\.\\d{2})(,.*)$")

    private fun fmt(totalMs: Long, withHours: Boolean, sep: String): String {
        val t = totalMs.coerceAtLeast(0)
        val h = t / 3_600_000
        val m = (t / 60_000) % 60
        val s = (t / 1000) % 60
        val ms = t % 1000
        return if (withHours || h > 0) "%02d:%02d:%02d%s%03d".format(h, m, s, sep, ms) else "%02d:%02d%s%03d".format(m, s, sep, ms)
    }

    private fun shiftCue(text: String, ms: Long) = cue.replace(text) { m ->
        val hasHours = m.groupValues[1].isNotEmpty()
        val h = m.groupValues[1].ifEmpty { "0" }.toLong()
        val total = h * 3_600_000 + m.groupValues[2].toLong() * 60_000 + m.groupValues[3].toLong() * 1000 + m.groupValues[5].toLong() + ms
        fmt(total, hasHours, m.groupValues[4])
    }

    private fun assTime(t: String, ms: Long): String {
        val p = t.split(':', '.')
        val total = p[0].toLong() * 3_600_000 + p[1].toLong() * 60_000 + p[2].toLong() * 1000 + p[3].toLong() * 10 + ms
        val c = total.coerceAtLeast(0)
        return "%d:%02d:%02d.%02d".format(c / 3_600_000, (c / 60_000) % 60, (c / 1000) % 60, (c % 1000) / 10)
    }

    fun shift(text: String, extension: String, ms: Long): String {
        if (ms == 0L) return text
        val isAss = extension.equals("ass", true) || extension.equals("ssa", true)
        return text.lineSequence().joinToString("\n") { line ->
            if (isAss) {
                ass.matchEntire(line)?.let { m -> m.groupValues[1] + assTime(m.groupValues[2], ms) + "," + assTime(m.groupValues[3], ms) + m.groupValues[4] } ?: line
            } else {
                val a = arrow.matchEntire(line)
                if (a != null && cue.containsMatchIn(a.groupValues[1])) shiftCue(a.groupValues[1], ms) + "-->" + shiftCue(a.groupValues[2], ms) else line
            }
        }
    }
}
