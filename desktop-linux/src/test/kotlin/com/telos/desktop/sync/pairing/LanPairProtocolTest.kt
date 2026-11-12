package com.telos.desktop.sync.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress

class LanPairProtocolTest {
    @Test
    fun generateCodeUsesSafeAlphabetAndLength() {
        repeat(20) {
            val code = LanPairProtocol.generateCode()
            assertEquals(LanPairProtocol.CODE_LENGTH, code.length)
            assertTrue(code.all { it in "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" })
            assertFalse(code.contains('0'))
            assertFalse(code.contains('O'))
            assertFalse(code.contains('I'))
            assertFalse(code.contains('1'))
        }
    }

    @Test
    fun buildAndParseUriRoundTrip() {
        val uri = LanPairProtocol.buildUri("ab23cd45", "192.168.1.42", 17890)
        assertEquals(
            "telos-sync://pair?code=AB23CD45&host=192.168.1.42&port=17890",
            uri,
        )
        val parsed = LanPairProtocol.parseUri(uri)
        assertNotNull(parsed)
        assertEquals("AB23CD45", parsed!!.code)
        assertEquals("192.168.1.42", parsed.host)
        assertEquals(17890, parsed.port)
        assertFalse(parsed.tls)
        assertEquals("http://192.168.1.42:17890", parsed.httpBaseUrl())
    }

    @Test
    fun parseUriAcceptsTlsFlagAndRejectsGarbage() {
        val withTls = LanPairProtocol.parseUri(
            "telos-sync://pair?code=ZZZZYYYY&host=10.0.0.2&port=19000&tls=1",
        )
        assertNotNull(withTls)
        assertTrue(withTls!!.tls)
        assertEquals(19000, withTls.port)
        assertNull(LanPairProtocol.parseUri(""))
        assertNull(LanPairProtocol.parseUri("https://example.com"))
        assertNull(LanPairProtocol.parseUri("telos-sync://pair?code=AB&host=10.0.0.1&port=1"))
        assertNull(LanPairProtocol.parseUri("telos-sync://pair?code=ABCD2345&host=&port=1"))
    }

    @Test
    fun bindAddressFilterRejectsPublicAndAcceptsPrivate() {
        assertTrue(LanPairProtocol.isPrivateOrLocalAddress(InetAddress.getByName("127.0.0.1")))
        assertTrue(LanPairProtocol.isPrivateOrLocalAddress(InetAddress.getByName("192.168.0.10")))
        assertTrue(LanPairProtocol.isPrivateOrLocalAddress(InetAddress.getByName("10.1.2.3")))
        assertTrue(LanPairProtocol.isPrivateOrLocalAddress(InetAddress.getByName("172.16.5.5")))
        assertTrue(LanPairProtocol.isPrivateOrLocalAddress(InetAddress.getByName("100.64.1.2")))
        assertFalse(LanPairProtocol.isPrivateOrLocalAddress(InetAddress.getByName("8.8.8.8")))
        assertFalse(LanPairProtocol.isPrivateOrLocalAddress(InetAddress.getByName("1.1.1.1")))
        try {
            LanPairProtocol.requirePrivateBindAddress("8.8.8.8")
            fail("expected public bind rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("private"))
        }
        val loopback = LanPairProtocol.requirePrivateBindAddress("127.0.0.1")
        assertTrue(loopback.isLoopbackAddress)
    }

    @Test
    fun tokenStoreRemembersDigestOnly() {
        val token = LanPairTokenStore.mintToken()
        assertTrue(token.length >= 32)
        val prev = System.getProperty("user.home")
        val tmp = kotlin.io.path.createTempDirectory("lan-pair-token").toFile()
        try {
            System.setProperty("user.home", tmp.absolutePath)
            assertTrue(LanPairTokenStore.remember(token, "device-a", "Pixel"))
            val index = LanPairTokenStore.indexFile().readText()
            assertFalse(index.contains(token))
            assertTrue(LanPairTokenStore.isAuthorized(token))
            assertFalse(LanPairTokenStore.isAuthorized("not-a-real-token"))
            val stored = LanPairTokenStore.lookup(token)
            assertEquals("device-a", stored!!.deviceId)
            assertEquals("Pixel", stored.deviceName)
        } finally {
            if (prev != null) System.setProperty("user.home", prev) else System.clearProperty("user.home")
            tmp.deleteRecursively()
        }
    }
}
