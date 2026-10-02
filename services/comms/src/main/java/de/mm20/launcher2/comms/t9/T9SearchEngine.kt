package de.mm20.launcher2.comms.t9

import de.mm20.launcher2.comms.model.DialerContact

/**
 * Matches a numeric dialpad input against contact names (via [T9Keypad]) and phone numbers.
 */
class T9SearchEngine {

    /**
     * @param query digits typed on the dialpad (e.g. "226" for "BAM"/"ΑΒΓ..."). An empty query
     * matches nothing - the dialpad shows no results until the user types something.
     * @return [contacts] whose name or any phone number matches [query], name matches ranked
     * first (a name match is a much stronger "this is who I meant" signal than any 3+ digit
     * numeric substring happening to appear in a phone number).
     */
    fun search(query: String, contacts: List<DialerContact>): List<DialerContact> {
        if (query.isEmpty()) return emptyList()

        val nameMatches = mutableListOf<DialerContact>()
        val numberMatches = mutableListOf<DialerContact>()

        for (contact in contacts) {
            when {
                matchesName(query, contact.displayName) -> nameMatches += contact
                matchesAnyNumber(query, contact.phoneNumbers) -> numberMatches += contact
            }
        }

        return nameMatches + numberMatches
    }

    /**
     * True if [query] is a T9 prefix of any individual word in [name] (the usual T9 UX - typing
     * "226" finds "Bambi" as well as "Anna Bambi", matching on the "Bambi" word) or a substring of
     * the whole name's digit sequence (catches mid-name/compound-name matches a per-word prefix
     * check alone would miss).
     */
    private fun matchesName(query: String, name: String): Boolean {
        val words = name.split(WORD_SEPARATORS).filter { it.isNotEmpty() }
        if (words.any { T9Keypad.digitsFor(it).startsWith(query) }) return true

        val wholeNameDigits = T9Keypad.digitsFor(name)
        return wholeNameDigits.contains(query)
    }

    private fun matchesAnyNumber(query: String, numbers: List<String>): Boolean {
        return numbers.any { number -> number.filter { it.isDigit() }.contains(query) }
    }

    companion object {
        private val WORD_SEPARATORS = Regex("\\s+")
    }
}
