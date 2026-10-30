package com.telos.desktop.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionNetGuardTest {
    @Test
    fun acceptsIpv4AndIpv6Cidrs() {
        val lines = SessionNetGuard.parseAllowEntries(
            listOf(
                "1.2.3.4",
                "10.0.0.0/8",
                "2001:db8::1",
                "2001:db8::/32",
                "# comment",
                "",
                "not a host",
                "999.1.1.1",
                "1.2.3.4/99",
                "01.2.3.4",
            ),
        )
        assertEquals(
            listOf(
                "        ip daddr 1.2.3.4 accept",
                "        ip daddr 10.0.0.0/8 accept",
                "        ip6 daddr 2001:db8::1 accept",
                "        ip6 daddr 2001:db8::/32 accept",
            ),
            lines,
        )
    }

    @Test
    fun tableDropsByDefault() {
        val rules = SessionNetGuard.nftRules(emptyList())
        assertTrue(rules.contains("policy drop"))
        assertTrue(rules.contains("udp dport 53 accept"))
        assertFalse(rules.contains("ip daddr"))
    }
}
