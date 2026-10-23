package de.mm20.launcher2.network.privatedns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivateDnsHostTest {
    @Test
    fun normalizesCaseAndInternationalHostnames() {
        assertEquals("dns.example", PrivateDnsHost.normalize(" DNS.Example. "))
        assertEquals("xn--bcher-kva.example", PrivateDnsHost.normalize("bücher.example"))
    }

    @Test
    fun rejectsIncompleteAndUnsafeHostnames() {
        listOf(
            "", "example", "https://dns.example", "dns.example/path", "-bad.example", "a..example",
            "dns.example..", "1.1.1.1",
        )
            .forEach { assertNull(PrivateDnsHost.normalize(it)) }
    }

    @Test
    fun enforcesDnsLabelAndHostnameLengths() {
        assertNull(PrivateDnsHost.normalize("${"a".repeat(64)}.example"))
        assertNull(PrivateDnsHost.normalize("${"a.".repeat(127)}a"))
    }
}
