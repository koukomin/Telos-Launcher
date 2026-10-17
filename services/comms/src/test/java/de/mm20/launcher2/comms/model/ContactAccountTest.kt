package de.mm20.launcher2.comms.model

import org.junit.Assert.*
import org.junit.Test

class ContactAccountTest {
    private val personal = ContactAccount("me@example.com", "com.google")
    private val work = ContactAccount("me@example.com", "com.exchange")
    private val contact = DialerContact(1, "Person", emptyList(), accounts = listOf(personal, work))

    @Test fun aggregateIsVisibleWhenAnyAccountIsSelected() {
        assertTrue(contact.isInAccounts(setOf(work.key)))
        assertTrue(contact.isInAccounts(setOf(personal.key)))
        assertFalse(contact.isInAccounts(setOf(ContactAccount("other@example.com", "com.google").key)))
    }

    @Test fun allAndEmptySelectionsAreDifferent() {
        assertTrue(contact.isInAccounts(null))
        assertFalse(contact.isInAccounts(emptySet()))
        assertTrue(DialerContact(2, "Legacy", emptyList()).isInAccounts(null))
    }

    @Test fun accountTypeIsPartOfIdentityAndKeysAreUnambiguous() {
        assertNotEquals(personal.key, work.key)
        assertNotEquals(ContactAccount("bc", "a").key, ContactAccount("c", "ab").key)
        assertEquals(ContactAccount(null, null).key, ContactAccount(null, null).key)
        assertFalse(personal.key.contains('\u0000')) // SharedPreferences XML-safe
    }
}
