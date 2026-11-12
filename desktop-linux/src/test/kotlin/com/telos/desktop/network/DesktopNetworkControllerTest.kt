package com.telos.desktop.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopNetworkControllerTest {
    @Test
    fun redactsWireGuardPrivateMaterial() {
        val raw = """
            [Interface]
            PrivateKey = abcSECRET
            PresharedKey = xyz
            Address = 10.0.0.2/32
        """.trimIndent()
        val redacted = DesktopNetworkController.redactWireGuardSecrets(raw)
        assertTrue(redacted.contains("PrivateKey = <redacted>"))
        assertTrue(redacted.contains("PresharedKey = <redacted>"))
        assertTrue(redacted.contains("10.0.0.2/32"))
        assertFalse(redacted.contains("abcSECRET"))
    }

    @Test
    fun parsesDnsLines() {
        assertEquals(
            DesktopNetworkController.DnsKind.Plain,
            DesktopNetworkController.parseDnsLine("1.1.1.1")?.kind,
        )
        assertEquals(
            "1.1.1.1",
            DesktopNetworkController.parseDnsLine("tls://1.1.1.1")?.address,
        )
        assertEquals(
            DesktopNetworkController.DnsKind.DoH,
            DesktopNetworkController.parseDnsLine("https://dns.cloudflare.com/dns-query")?.kind,
        )
    }

    @Test
    fun wireguardPathsAndInterfaceName() {
        assertEquals("telos-wg.conf", DesktopNetworkController.WIREGUARD_CONF_NAME)
        val name = DesktopNetworkController.interfaceNameFromConf(
            """
            [Interface]
            Name = wg-demo
            PrivateKey = x
            """.trimIndent(),
        )
        assertEquals("wg-demo", name)
    }

    @Test
    fun torRcContainsDataDirectoryAndSocks() {
        val data = java.io.File("/tmp/tor-data")
        val pid = java.io.File("/tmp/tor.pid")
        val rc = DesktopNetworkController.buildTorRc(data, pid, 9050)
        assertTrue(rc.contains("DataDirectory ${data.absolutePath}"))
        assertTrue(rc.contains("SocksPort 9050"))
        assertTrue(rc.contains("PidFile ${pid.absolutePath}"))
    }
}
