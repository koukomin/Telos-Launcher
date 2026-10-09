package de.mm20.launcher2.comms.search

import de.mm20.launcher2.comms.model.DialerContact

object ContactSearch {
    /** Contacts matching [query] by name, phone number or e-mail (Greek aware, see [TelosSearch]). */
    fun search(query: String, contacts: List<DialerContact>): List<DialerContact> {
        if (query.isBlank()) return contacts
        return TelosSearch.filter(contacts, query) { c ->
            listOf(c.displayName) + c.phoneNumbers + c.emails
        }
    }
}
