package de.mm20.launcher2.ktx

import org.junit.Assert.*
import org.junit.Test

class PhoneNumberKeyTest {
    @Test fun greekLandlineAndMobileAliasesHaveTheSameIdentity() {
        for (number in listOf("2100000000", "6900000000")) {
            val aliases = listOf(number, "+30 $number", "0030$number", "(+30) $number")
            aliases.forEach { a -> aliases.forEach { b -> assertTrue(PhoneNumberKey.matches(a, b)) } }
        }
    }
    @Test fun unrelatedCountryShortNumberAndExtensionCannotMatchBySuffix() {
        assertFalse(PhoneNumberKey.matches("+442100000000", "2100000000"))
        assertFalse(PhoneNumberKey.matches("0000000", "2100000000"))
        assertFalse(PhoneNumberKey.matches("2100000000;123", "2100000000"))
        assertFalse(PhoneNumberKey.matches("", ""))
        assertFalse(PhoneNumberKey.matches("+", "+"))
        assertTrue(PhoneNumberKey.matches("0033123456789", "+33123456789"))
    }
}
