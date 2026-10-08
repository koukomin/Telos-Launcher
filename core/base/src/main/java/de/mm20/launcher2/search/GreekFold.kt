package de.mm20.launcher2.search

import java.text.Normalizer

/**
 * Makes Greek text, Greek text without accents and Greek written with Latin letters (Greeklish) comparable.
 * Both sides of a comparison go through [fold]: "καλημέρα", "Καλημερα", "kalimera" and "kalhmera" all become
 * "kalimera". The result is a phonetic skeleton, not readable text, so it is only for matching.
 * Greeklish has no standard, so the fold is deliberately loose: letters that sound alike are merged
 * (i, y, h, ei, oi -> i; o, w -> o; b, v, f -> v; th -> t; ch, kh, x, ks -> x and so on).
 */
object GreekFold {

    private val COMBINING = Regex("\\p{M}+")

    fun hasGreek(text: String): Boolean = text.any { it in 'Ͱ'..'Ͽ' || it in 'ἀ'..'῿' }

    /** Lower case, no accents, final sigma as sigma. Greek stays Greek. */
    fun stripAccents(text: String): String {
        // Fast path (most text): plain ASCII has nothing to decompose and no final sigma
        if (text.all { it.code < 0x80 }) return text.lowercase()
        return stripAccentsSlow(text)
    }

    private fun stripAccentsSlow(text: String): String =
        COMBINING.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase().replace('ς', 'σ')

    /** Greek letters to Latin letters, by sound. Everything else is kept. The input has to be lower case and without accents. */
    private fun greekToLatin(s: String): String {
        val out = StringBuilder(s.length + 8)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val n = if (i + 1 < s.length) s[i + 1] else ' '
            val pair = when {
                c == 'α' && n == 'υ' -> "av"
                c == 'ε' && n == 'υ' -> "ev"
                c == 'ο' && n == 'υ' -> "u"
                c == 'α' && n == 'ι' -> "e"
                c == 'ε' && n == 'ι' -> "i"
                c == 'ο' && n == 'ι' -> "i"
                c == 'υ' && n == 'ι' -> "i"
                c == 'μ' && n == 'π' -> "b"
                c == 'ν' && n == 'τ' -> "d"
                c == 'γ' && (n == 'κ' || n == 'γ') -> "g"
                else -> null
            }
            if (pair != null) { out.append(pair); i += 2; continue }
            out.append(
                when (c) {
                    'α' -> "a"; 'β' -> "v"; 'γ' -> "g"; 'δ' -> "d"; 'ε' -> "e"; 'ζ' -> "z"; 'η' -> "i"; 'θ' -> "t"
                    'ι' -> "i"; 'κ' -> "k"; 'λ' -> "l"; 'μ' -> "m"; 'ν' -> "n"; 'ξ' -> "x"; 'ο' -> "o"; 'π' -> "p"
                    'ρ' -> "r"; 'σ' -> "s"; 'τ' -> "t"; 'υ' -> "i"; 'φ' -> "v"; 'χ' -> "x"; 'ψ' -> "ps"; 'ω' -> "o"
                    else -> c.toString()
                }
            )
            i++
        }
        return out.toString()
    }

    /** Merges the Latin spellings that stand for the same Greek sound */
    /** First step of [fold]: Greek letters become Latin letters. Text without Greek is returned as it is. */
    fun prepare(text: String): String = if (hasGreek(text)) greekToLatin(stripAccents(text)) else text

    /** Last step of [fold]: merges Latin spellings of the same sound. Expects lower case text without accents. */
    fun finish(text: String): String = latinFold(text)

    private fun latinFold(s: String): String {
        var t = s
        for ((from, to) in LATIN_RULES) t = t.replace(from, to)
        val out = StringBuilder(t.length)
        for (c in t) {
            val m = when (c) { 'y', 'h' -> 'i'; 'w' -> 'o'; 'b', 'f' -> 'v'; 'c', 'q' -> 'k'; 'j' -> 'z'; else -> c }
            if (out.isEmpty() || out.last() != m) out.append(m)
        }
        return out.toString()
    }

    private val LATIN_RULES = listOf(
        "ou" to "u", "ai" to "e", "ei" to "i", "oi" to "i", "ui" to "i",
        "au" to "av", "eu" to "ev",
        "mp" to "b", "nt" to "d", "gk" to "g", "gg" to "g",
        "th" to "t", "dh" to "d", "ch" to "x", "kh" to "x", "ks" to "x", "ph" to "f",
        "8" to "t", "3" to "x",
    )

    /** Whether [text] contains [query], ignoring case, accents and the Greek or Latin spelling */
    fun contains(text: String, query: String): Boolean = fold(query).let { it.isEmpty() || fold(text).contains(it) }

    /** The key two spellings of the same Greek word have in common. Latin text is folded too, which only makes matching looser. */
    fun fold(text: String): String = latinFold(greekToLatin(stripAccents(text)))

    /** True if [query] contains letters, i.e. SQL LIKE could miss accented or greeklish matches and an in-memory fold match is needed. */
    fun needsFold(query: String): Boolean = query.any { it.isLetter() }

    /** [foldedQuery] must be the result of [fold]. */
    fun matches(candidate: String?, foldedQuery: String): Boolean =
        !candidate.isNullOrEmpty() && foldedQuery.isNotEmpty() && fold(candidate).contains(foldedQuery)
}
