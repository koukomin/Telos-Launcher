package de.mm20.launcher2.network.nextdns

import org.junit.Assert.*
import org.junit.Test

class NextDnsValidationTest {
    @Test fun domainsNormalizeCaseTrailingDotAndInternationalNames() {
        assertEquals("example.org",NextDnsValidation.domain(" EXAMPLE.org. "))
        assertEquals("xn--bcher-kva.example",NextDnsValidation.domain("bücher.example"))
        assertEquals("abc123",NextDnsValidation.profileId(" ABC123 "))
    }
    @Test fun urlsIpAddressesAndInjectedPathsNeverBecomeRules() {
        listOf("https://example.org", "example.org/../x", "example.org?query", "example.org#fragment", "alice@example.org", "*.example.org", "example..org", "-example.org", "example.org\nX:1", "127.0.0.1", "localhost", "example.org%2Fbad").forEach {
            try { NextDnsValidation.domain(it); fail("Accepted invalid domain") }
            catch (failure: NextDnsException) { assertEquals(NextDnsErrorKind.INVALID,failure.kind) }
        }
    }
    @Test fun profileAndApiCredentialCannotInjectHeadersOrSelectAnotherOrigin() {
        listOf("../abc123", "abcdef/", "https://example.org", "abc123?x=1", "abcdef\r\n").forEach {
            try { NextDnsValidation.profileId(it); fail("Accepted invalid profile") }
            catch (_: NextDnsException) { }
        }
        listOf("short", "validvalidvalidvalid\r\nX:evil", "valid valid valid valid", "κλειδίκλειδίκλειδί").forEach {
            try { NextDnsValidation.apiKey(it); fail("Accepted invalid key") }
            catch (_: NextDnsException) { }
        }
    }
}
