package de.mm20.launcher2.comms.search

/**
 * The matcher shared by all Telos apps.
 *
 * The query is split on whitespace; every token has to match somewhere in the fields (AND).
 * Matching is case and accent insensitive, Greek and Latin text match each other
 * (Greeklish: "giannis" finds "Γιάννης", "γιαννης" finds "Giannis") and digit tokens match phone
 * numbers regardless of spaces, dashes and a leading + or 00.
 *
 * Both sides are folded to a loose phonetic skeleton (see [skeleton]) in addition to the plain
 * accent-folded text, so no state has to be precomputed. Pure Kotlin, O(items * fields * tokens).
 */
object TelosSearch {

    /** True if every token of [query] matches one of [fields]. A blank query matches everything. */
    fun matches(query: String, vararg fields: String?): Boolean = scoreList(query, fields.asList()) > 0

    /**
     * 0 = no match, higher is better: exact word > prefix of a word > word contains > substring.
     * A blank query scores 1 for everything.
     */
    fun score(query: String, vararg fields: String?): Int = scoreList(query, fields.asList())

    /**
     * Keeps only the items that match. The order is kept for a blank query, otherwise the best
     * matches come first (stable for equal scores).
     */
    fun <T> filter(items: List<T>, query: String, fields: (T) -> List<String?>): List<T> {
        if (query.isBlank()) return items
        val scored = ArrayList<Pair<T, Int>>()
        for (item in items) {
            val s = scoreList(query, fields(item))
            if (s > 0) scored.add(item to s)
        }
        return scored.sortedByDescending { it.second }.map { it.first }
    }

    // ---------------------------------------------------------------------------------------

    private class Token(
        val plain: String,
        val greek: String?,
        val sk1: String,
        val sk2: String,
        val digits: String?,
    )

    private class Field(val words: List<String>, val sk1: List<String>, val sk2: List<String>, val digits: String)

    private fun scoreList(query: String, fields: List<String?>): Int {
        val tokens = query.split(WHITESPACE).mapNotNull { buildToken(it) }
        if (tokens.isEmpty()) return 1
        val prepared = fields.map { f -> if (f.isNullOrEmpty()) null else buildField(f) }
        var total = 0
        for (token in tokens) {
            var best = 0
            for ((index, field) in prepared.withIndex()) {
                if (field == null) continue
                val tier = tier(token, field)
                if (tier == 0) continue
                val s = tier * 10 + (if (index == 0) 3 else 0)
                if (s > best) best = s
            }
            if (best == 0) return 0
            total += best
        }
        return total
    }

    private fun buildToken(raw: String): Token? {
        val plain = clean(raw)
        if (plain.isEmpty()) return null
        val digitsOnly = raw.any { it.isDigit() } && raw.all { it.isDigit() || it in PHONE_PUNCT }
        val hasLetter = plain.any { it.isLetter() }
        val greek = if (GreekText.looksLatin(plain) && plain.none { it in 'Ͱ'..'Ͽ' }) {
            GreekText.latinToGreek(plain)
        } else null
        val raw2 = greekRaw(plain)
        return Token(
            plain = plain,
            greek = greek,
            sk1 = if (hasLetter) skeleton(raw2, false) else "",
            sk2 = if (hasLetter) skeleton(raw2, true) else "",
            digits = if (digitsOnly) stripZeros(raw.filter { it.isDigit() }) else null,
        )
    }

    private fun buildField(text: String): Field {
        val folded = foldMarks(text)
        val words = splitWords(folded)
        val raws = words.map { greekRaw(it) }
        return Field(
            words = words,
            sk1 = raws.map { skeleton(it, false) },
            sk2 = raws.map { skeleton(it, true) },
            digits = stripZeros(text.filter { it.isDigit() }),
        )
    }

    private fun tier(t: Token, f: Field): Int {
        var best = 0
        for (i in f.words.indices) {
            val w = f.words[i]
            best = maxOf(best, cmp(w, t.plain))
            if (t.greek != null) best = maxOf(best, cmp(w, t.greek))
            if (t.sk1.isNotEmpty()) best = maxOf(best, cmp(f.sk1[i], t.sk1))
            if (t.sk2.isNotEmpty()) best = maxOf(best, cmp(f.sk2[i], t.sk2))
            if (best == 4) return 4
        }
        if (t.digits != null && t.digits.isNotEmpty() && f.digits.isNotEmpty()) {
            best = maxOf(best, cmp(f.digits, t.digits))
        }
        if (best == 0 && f.words.size > 1) {
            // substring across word borders / punctuation, e.g. "johnsmith" or "a-b"
            if (f.words.joinToString("").contains(t.plain)) best = 1
            else if (t.greek != null && f.words.joinToString("").contains(t.greek)) best = 1
            else if (t.sk2.isNotEmpty() && f.sk2.joinToString("").contains(t.sk2)) best = 1
        }
        return best
    }

    private fun cmp(text: String, token: String): Int = when {
        token.isEmpty() || text.isEmpty() -> 0
        text == token -> 4
        text.startsWith(token) -> 3
        text.contains(token) -> 2
        else -> 0
    }

    private fun stripZeros(digits: String): String =
        if (digits.startsWith("00")) digits.substring(2) else digits

    // ---------------------------------------------------------------------------------------

    /** Lowercase, accents and tonos removed, final sigma folded, apostrophes removed. */
    private fun foldMarks(text: String): String =
        GreekText.fold(text).filterNot { it in IGNORED }

    private fun splitWords(folded: String): List<String> {
        val words = ArrayList<String>()
        val sb = StringBuilder()
        for (c in folded) {
            if (c.isLetterOrDigit()) sb.append(c)
            else if (sb.isNotEmpty()) {
                words.add(sb.toString()); sb.setLength(0)
            }
        }
        if (sb.isNotEmpty()) words.add(sb.toString())
        return words
    }

    /** Folded token without any separators or punctuation. */
    private fun clean(token: String): String = foldMarks(token).filter { it.isLetterOrDigit() }

    private const val VOICELESS = "κξπστφχψθ"

    /** Greek (folded) to a Latin spelling, other characters are kept. */
    private fun greekRaw(s: String): String {
        if (s.none { it in 'Ͱ'..'Ͽ' }) return s
        val out = StringBuilder(s.length * 2)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val n = s.getOrNull(i + 1)
            if ((c == 'α' || c == 'ε') && n == 'υ') {
                out.append(if (c == 'α') 'a' else 'e')
                val after = s.getOrNull(i + 2)
                out.append(if (after != null && after in VOICELESS) 'f' else 'v')
                i += 2
                continue
            }
            if (c == 'ο' && n == 'υ') { out.append("ou"); i += 2; continue }
            if (c == 'γ' && n == 'κ') { out.append("gk"); i += 2; continue }
            if (c == 'γ' && n == 'γ') { out.append("ng"); i += 2; continue }
            if (c == 'μ' && n == 'π') { out.append("mp"); i += 2; continue }
            if (c == 'ν' && n == 'τ') { out.append("nt"); i += 2; continue }
            out.append(GREEK_RAW[c] ?: c.toString())
            i++
        }
        return out.toString()
    }

    /**
     * Collapses a Latin spelling to a loose phonetic skeleton: all i-sounds (i, y, u, h, ei, oi, ou)
     * are one, e/ai, o/w, b/v/mp/mb, k/c/q, ks/ch/x, nt/nd, th/8 and doubled letters collapse.
     * [reduced] additionally treats g/y/j(+i) before a vowel as one sound ("Yiannis" = "Giannis")
     * and drops h ("John" = "Jon").
     */
    private fun skeleton(s: String, reduced: Boolean): String {
        val sb = StringBuilder(s.length)
        fun put(c: Char) {
            if (sb.isEmpty() || sb[sb.length - 1] != c) sb.append(c)
        }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val n = s.getOrNull(i + 1)
            val n2 = s.getOrNull(i + 2)
            if (reduced) {
                if ((c == 'y' || c == 'j') ) {
                    put('g')
                    i += if (n == 'i' || n == 'y') 2 else 1
                    continue
                }
                if (c == 'g' && (n == 'i' || n == 'y') && n2 != null && n2 in "aeou") {
                    put('g'); i += 2; continue
                }
                if (c == 'h') { i++; continue }
            }
            if (n != null) {
                var handled = true
                when ("" + c + n) {
                    "th" -> put('8')
                    "ps" -> put('P')
                    "ph" -> put('f')
                    "ch", "kh", "ks" -> put('X')
                    "dh" -> put('d')
                    "mp", "mb" -> put('v')
                    "nt", "nd" -> put('D')
                    "gk" -> put('g')
                    "gg" -> { put('n'); put('g') }
                    "ou", "ei", "oi", "ui" -> put('i')
                    "ai" -> put('e')
                    else -> handled = false
                }
                if (handled) { i += 2; continue }
            }
            when (c) {
                'b', 'v' -> put('v')
                'c', 'q', 'k' -> put('k')
                'i', 'u', 'y', 'h' -> put('i')
                'j' -> put('g')
                'o', 'w' -> put('o')
                'x' -> put('X')
                '3' -> put('e')
                else -> put(c)
            }
            i++
        }
        return sb.toString()
    }

    private val WHITESPACE = Regex("\\s+")
    private const val PHONE_PUNCT = "+-.()/"
    private val IGNORED = setOf('´', '΄', '\'', '’', 'ʼ', '᾽', '`', '´', '΄')

    private val GREEK_RAW = mapOf(
        'α' to "a", 'β' to "v", 'γ' to "g", 'δ' to "d", 'ε' to "e",
        'ζ' to "z", 'η' to "i", 'θ' to "th", 'ι' to "i", 'κ' to "k",
        'λ' to "l", 'μ' to "m", 'ν' to "n", 'ξ' to "x", 'ο' to "o",
        'π' to "p", 'ρ' to "r", 'σ' to "s", 'τ' to "t", 'υ' to "u",
        'φ' to "f", 'χ' to "ch", 'ψ' to "ps", 'ω' to "o",
    )
}
