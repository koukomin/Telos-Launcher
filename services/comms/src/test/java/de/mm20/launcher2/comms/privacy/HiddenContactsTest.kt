package de.mm20.launcher2.comms.privacy

import de.mm20.launcher2.comms.model.DialerContact
import org.junit.Assert.*
import org.junit.Test

class HiddenContactsTest {
    @Test fun legacyFirstNumberHidesAllNumbersAndHistoryAliases() {
        val contact = DialerContact(1, "Δοκιμή", listOf("2100000000", "6900000000", "+30 2310000000"))
        val hidden = HiddenContacts.expanded(mapOf("00302100000000" to "1"), listOf(contact))
        for (number in listOf("+302100000000", "00306900000000", "2310000000"))
            assertTrue("History leaked $number", HiddenContacts.matches(number, hidden))
        assertFalse(HiddenContacts.matches("+442100000000", hidden))
        assertFalse(HiddenContacts.matches("6900000001", hidden))
        assertTrue(HiddenContacts.contactHidden(contact, hidden))
    }
}
