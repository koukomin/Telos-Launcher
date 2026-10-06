package de.mm20.launcher2.comms.search

import java.text.Normalizer

object GreekText {
    fun fold(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        val stripped = COMBINING.replace(nfd, "")
        return stripped.lowercase().replace('ς', 'σ')
    }

    fun latinToGreek(foldedLatin: String): String {
        val out = StringBuilder(foldedLatin.length)
        var i = 0
        while (i < foldedLatin.length) {
            val pair = if (i + 1 < foldedLatin.length) foldedLatin.substring(i, i + 2) else ""
            val mappedPair = DIGRAPHS[pair]
            if (mappedPair != null) {
                out.append(mappedPair)
                i += 2
            } else {
                out.append(SINGLE[foldedLatin[i]] ?: foldedLatin[i])
                i++
            }
        }
        return out.toString()
    }

    fun greekToLatin(foldedGreek: String): String {
        val out = StringBuilder(foldedGreek.length * 2)
        var i = 0
        while (i < foldedGreek.length) {
            val pair = if (i + 1 < foldedGreek.length) foldedGreek.substring(i, i + 2) else ""
            when (pair) {
                "γκ" -> { out.append("gk"); i += 2 }
                "ντ" -> { out.append("nt"); i += 2 }
                "μπ" -> { out.append("mp"); i += 2 }
                else -> {
                    out.append(GREEK_TO_LATIN[foldedGreek[i]] ?: foldedGreek[i].toString())
                    i++
                }
            }
        }
        return out.toString()
    }

    fun looksLatin(text: String): Boolean =
        text.any { it in 'a'..'z' || it in 'A'..'Z' }

    fun looksGreek(text: String): Boolean =
        text.any { it in '\u0370'..'\u03FF' || it in '\u1F00'..'\u1FFF' }

    private val COMBINING = Regex("\\p{M}+")

    private val DIGRAPHS = mapOf(
        "th" to "θ",
        "ps" to "ψ",
        "ch" to "χ",
        "ph" to "φ",
        "dh" to "δ",
        "ks" to "ξ",
        "ou" to "ου",
        "ai" to "αι",
        "ei" to "ει",
        "oi" to "οι",
        "gk" to "γκ",
        "nt" to "ντ",
        "mp" to "μπ",
    )

    private val SINGLE = mapOf(
        'a' to 'α', 'b' to 'β', 'c' to 'κ', 'd' to 'δ', 'e' to 'ε',
        'f' to 'φ', 'g' to 'γ', 'h' to 'η', 'i' to 'ι', 'j' to 'ζ',
        'k' to 'κ', 'l' to 'λ', 'm' to 'μ', 'n' to 'ν', 'o' to 'ο',
        'p' to 'π', 'q' to 'κ', 'r' to 'ρ', 's' to 'σ', 't' to 'τ',
        'u' to 'υ', 'v' to 'β', 'w' to 'ω', 'x' to 'ξ', 'y' to 'υ',
        'z' to 'ζ',
    )

    private val GREEK_TO_LATIN = mapOf(
        'α' to "a", 'β' to "b", 'γ' to "g", 'δ' to "d", 'ε' to "e",
        'ζ' to "z", 'η' to "i", 'θ' to "th", 'ι' to "i", 'κ' to "k",
        'λ' to "l", 'μ' to "m", 'ν' to "n", 'ξ' to "x", 'ο' to "o",
        'π' to "p", 'ρ' to "r", 'σ' to "s", 'τ' to "t", 'υ' to "y",
        'φ' to "f", 'χ' to "ch", 'ψ' to "ps", 'ω' to "o",
    )
}
