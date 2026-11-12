package com.telos.desktop.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    @Test
    fun parsesAppRoutingAllowDenyVpn() {
        val routes = DesktopNetworkController.parseAppRoutingLines(
            listOf(
                "# comment",
                "firefox.desktop deny",
                "org.mozilla.firefox vpn",
                "com.example.app allow",
                "bad",
                "evil/path deny",
                "weird mode",
            ),
        )
        assertEquals(
            listOf(
                DesktopNetworkController.AppRoute("firefox.desktop", DesktopNetworkController.AppRouteMode.Deny),
                DesktopNetworkController.AppRoute("org.mozilla.firefox", DesktopNetworkController.AppRouteMode.Vpn),
                DesktopNetworkController.AppRoute("com.example.app", DesktopNetworkController.AppRouteMode.Allow),
            ),
            routes,
        )
        assertFalse(DesktopNetworkController.appRoutingEnforced())
    }

    @Test
    fun killSwitchPreferenceRoundTrip() {
        val dir = File.createTempFile("telos-net", ".dir").also {
            it.delete()
            it.mkdirs()
        }
        val file = File(dir, "kill-switch.txt")
        file.writeText("# comment\ntrue\n")
        assertTrue(
            file.readLines().map { it.trim() }.first { it.isNotEmpty() && !it.startsWith("#") }
                .equals("true", ignoreCase = true),
        )
        file.writeText("false\n")
        assertEquals("false", file.readLines().first().trim())
        dir.deleteRecursively()
    }

    @Test
    fun serializeAppRoutesIncludesTemplateAndRows() {
        val text = DesktopNetworkController.serializeAppRoutes(
            listOf(
                DesktopNetworkController.AppRoute("a.desktop", DesktopNetworkController.AppRouteMode.Deny),
            ),
        )
        assertTrue(text.contains("app-routing.txt"))
        assertTrue(text.contains("a.desktop deny"))
        assertEquals(
            listOf(DesktopNetworkController.AppRoute("a.desktop", DesktopNetworkController.AppRouteMode.Deny)),
            DesktopNetworkController.parseAppRoutingLines(text.lines()),
        )
    }
}
