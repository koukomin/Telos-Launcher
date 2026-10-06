package de.mm20.launcher2.comms.search

import de.mm20.launcher2.comms.model.DialerContact

object ContactSearch {
    /**
     * Rank: 0 native-script prefix, 1 native contains, 2 number, 3 greeklish/transliteration.
     * Latin query matching a Latin stored name beats the same query matching a Greek name via
     * greeklish.
     */
    fun search(query: String, contacts: List<DialerContact>): List<DialerContact> {
        val q = query.trim()
        if (q.isEmpty()) return contacts
        return contacts.mapNotNull { contact ->
            rank(q, contact)?.let { contact to it }
        }.sortedWith(
            compareBy({ it.second.rank }, { if (it.second.prefix) 0 else 1 }, { it.first.displayName.lowercase() })
        ).map { it.first }
    }

    private fun rank(query: String, contact: DialerContact): Rank? {
        nameRank(query, contact.displayName)?.let { return it }
        if (contact.phoneNumbers.any { it.contains(query.filter { ch -> ch.isDigit() || ch == '+' }) && query.any(Char::isDigit) } ||
            contact.phoneNumbers.any { it.contains(query, ignoreCase = true) }
        ) {
            return Rank(2, prefix = false)
        }
        if (contact.emails.any { it.contains(query, ignoreCase = true) }) {
            return Rank(2, prefix = false)
        }
        return null
    }

    private fun nameRank(query: String, name: String): Rank? {
        val qFold = GreekText.fold(query)
        val nFold = GreekText.fold(name)
        if (qFold.isEmpty() || nFold.isEmpty()) return null
        val words = nFold.split(WHITESPACE).filter { it.isNotEmpty() }

        val nativePrefix = nFold.startsWith(qFold) || words.any { it.startsWith(qFold) }
        val nativeContains = nFold.contains(qFold)
        if (nativePrefix) return Rank(0, prefix = true)
        if (nativeContains) return Rank(1, prefix = false)

        val queryLatin = GreekText.looksLatin(query)
        val nameGreek = GreekText.looksGreek(name)
        if (queryLatin && nameGreek) {
            val asGreek = GreekText.latinToGreek(qFold)
            val prefix = nFold.startsWith(asGreek) || words.any { it.startsWith(asGreek) }
            if (prefix || nFold.contains(asGreek)) return Rank(3, prefix)
            val nameAsLatin = GreekText.greekToLatin(nFold)
            val latinPrefix = nameAsLatin.startsWith(qFold) ||
                nameAsLatin.split(WHITESPACE).any { it.startsWith(qFold) }
            if (latinPrefix || nameAsLatin.contains(qFold)) return Rank(3, latinPrefix)
        }
        return null
    }

    private data class Rank(val rank: Int, val prefix: Boolean)

    private val WHITESPACE = Regex("\\s+")
}
