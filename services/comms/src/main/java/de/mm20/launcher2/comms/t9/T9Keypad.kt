package de.mm20.launcher2.comms.t9

/**
 * Letter-to-digit mapping for a standard 12-key phone keypad, covering both the Latin alphabet
 * (the usual English T9 layout) and the Greek alphabet (24 letters split evenly into the same
 * eight groups of three, in alphabetical order - the layout printed on Greek phone keypads).
 */
object T9Keypad {

    private val digitByLetter: Map<Char, Char> = buildMap {
        // Latin - standard T9 layout (26 letters: 7/9 keys get 4 letters, the rest get 3).
        mapLetters("ABC", '2')
        mapLetters("DEF", '3')
        mapLetters("GHI", '4')
        mapLetters("JKL", '5')
        mapLetters("MNO", '6')
        mapLetters("PQRS", '7')
        mapLetters("TUV", '8')
        mapLetters("WXYZ", '9')

        // Greek - 24 letters, evenly split into 8 groups of 3, in alphabetical order.
        mapLetters("ΑΒΓ", '2') // Α Β Γ
        mapLetters("ΔΕΖ", '3') // Δ Ε Ζ
        mapLetters("ΗΘΙ", '4') // Η Θ Ι
        mapLetters("ΚΛΜ", '5') // Κ Λ Μ
        mapLetters("ΝΞΟ", '6') // Ν Ξ Ο
        mapLetters("ΠΡΣ", '7') // Π Ρ Σ
        mapLetters("ΤΥΦ", '8') // Τ Υ Φ
        mapLetters("ΧΨΩ", '9') // Χ Ψ Ω
    }

    private fun MutableMap<Char, Char>.mapLetters(letters: String, digit: Char) {
        for (letter in letters) put(letter, digit)
    }

    /** The keypad digit for [letter] (case-insensitive, Latin or Greek), or null if it has none. */
    fun digitFor(letter: Char): Char? = digitByLetter[letter.uppercaseChar()]

    /**
     * [text] with every mapped letter replaced by its digit and every unmapped character (spaces,
     * punctuation, digits already in the name, other scripts) dropped entirely - this is what's
     * compared against the user's dialpad input.
     */
    fun digitsFor(text: String): String {
        val builder = StringBuilder(text.length)
        for (char in text) {
            digitFor(char)?.let { builder.append(it) }
        }
        return builder.toString()
    }
}
