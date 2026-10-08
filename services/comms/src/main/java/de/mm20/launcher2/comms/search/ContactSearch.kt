package de.mm20.launcher2.comms.search

import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.search.GreekFold

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
        val qFold = GreekFold.fold(query.trim())
        val nFold = GreekFold.fold(name)
        if (qFold.isEmpty() || nFold.isEmpty()) return null
        // fold word by word so that a word prefix is judged on the folded word
        val words = name.split(WHITESPACE).filter { it.isNotEmpty() }.map { GreekFold.fold(it) }

        val prefix = nFold.startsWith(qFold) || words.any { it.startsWith(qFold) }
        if (!prefix && !nFold.contains(qFold)) return null
        // Latin query matching a Greek name (greeklish) ranks below a same-script match
        val crossScript = GreekText.looksLatin(query) && GreekFold.hasGreek(name) && !GreekFold.hasGreek(query)
        return when {
            crossScript -> Rank(3, prefix)
            prefix -> Rank(0, prefix = true)
            else -> Rank(1, prefix = false)
        }
    }

    private data class Rank(val rank: Int, val prefix: Boolean)

    private val WHITESPACE = Regex("\\s+")
}
