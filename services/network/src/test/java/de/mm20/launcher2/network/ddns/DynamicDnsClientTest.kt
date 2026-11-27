package de.mm20.launcher2.network.ddns

import org.junit.Assert.*
import org.junit.Test

class DynamicDnsClientTest {
    @Test fun duckDnsSeparatesIpv6AndRejectsOtherDomains() {
        val provider = DynamicDnsProvider.DuckDns
        val request = checkNotNull(DynamicDnsClient.request(provider, "telos.duckdns.org", "2001:db8::1", "test-token", "", ""))
        assertEquals("www.duckdns.org", request.url.host)
        assertEquals("telos", request.url.queryParameter("domains"))
        assertEquals("2001:db8::1", request.url.queryParameter("ipv6"))
        assertNull(request.url.queryParameter("ip"))
        assertNull(DynamicDnsClient.request(provider, "telos.example.com", "192.0.2.1", "test-token", "", ""))
    }

    @Test fun updateCredentialsStayInAuthorizationHeaderForNoIp() {
        val provider = DynamicDnsProvider.NoIp
        val request = checkNotNull(DynamicDnsClient.request(provider, "telos.example.com", "192.0.2.1", "test-token", "user", ""))
        assertTrue(request.header("Authorization")!!.startsWith("Basic "))
        assertFalse(request.url.toString().contains("test-token"))
        assertEquals("192.0.2.1", request.url.queryParameter("myip"))
        assertNull(DynamicDnsClient.request(provider, "telos.example.com", "192.0.2.256", "test-token", "user", ""))
        assertNull(DynamicDnsClient.request(provider, "telos.example.com", "example.com", "test-token", "user", ""))
    }
}
