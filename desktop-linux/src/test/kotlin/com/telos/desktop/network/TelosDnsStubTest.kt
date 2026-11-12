package com.telos.desktop.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class TelosDnsStubTest {
    @Test
    fun blocklistMatchesExactAndSubdomain() {
        val block = listOf("ads.example.com", "tracker.test")
        val allow = emptyList<String>()
        assertTrue(TelosDnsStub.isQueryBlocked("ads.example.com", block, allow))
        assertTrue(TelosDnsStub.isQueryBlocked("cdn.ads.example.com", block, allow))
        assertTrue(TelosDnsStub.isQueryBlocked("Tracker.Test.", block, allow))
        assertFalse(TelosDnsStub.isQueryBlocked("example.com", block, allow))
        assertFalse(TelosDnsStub.isQueryBlocked("good.example", block, allow))
    }

    @Test
    fun allowlistOverridesBlocklist() {
        val block = listOf("example.com")
        val allow = listOf("ok.example.com")
        assertTrue(TelosDnsStub.isQueryBlocked("bad.example.com", block, allow))
        assertFalse(TelosDnsStub.isQueryBlocked("ok.example.com", block, allow))
        assertFalse(TelosDnsStub.isQueryBlocked("a.ok.example.com", block, allow))
    }

    @Test
    fun wildcardBlockMatchesSuffix() {
        val block = listOf("*.blocked.test")
        assertTrue(TelosDnsStub.isQueryBlocked("a.blocked.test", block, emptyList()))
        assertTrue(TelosDnsStub.isQueryBlocked("blocked.test", block, emptyList()))
        assertFalse(TelosDnsStub.isQueryBlocked("blocked.test.evil", block, emptyList()))
    }

    @Test
    fun extractQnameAndNxDomainPreserveId() {
        val query = buildDnsQuery(0xABCD, "ads.example.com")
        assertEquals("ads.example.com", TelosDnsStub.extractQname(query))
        val nx = TelosDnsStub.buildNxDomain(query)
        assertNotNull(nx)
        assertEquals(0xAB.toByte(), nx!![0])
        assertEquals(0xCD.toByte(), nx[1])
        // QR bit set, rcode NXDOMAIN (3)
        assertTrue(nx[2].toInt() and 0x80 != 0)
        assertEquals(3, nx[3].toInt() and 0x0F)
    }

    @Test
    fun stubBindsLocalhostAndAnswersNxDomainWithoutRoot() {
        val port = freeUdpPort()
        assertTrue(
            TelosDnsStub.start(
                port = port,
                blocklist = listOf("blocked.test"),
                allowlist = emptyList(),
                upstream = InetAddress.getByName("9.9.9.9"),
            ),
        )
        try {
            assertTrue(TelosDnsStub.isRunning())
            assertEquals(port, TelosDnsStub.listeningPort())
            val query = buildDnsQuery(0x1234, "blocked.test")
            DatagramSocket().use { sock ->
                sock.soTimeout = 2000
                sock.send(
                    DatagramPacket(
                        query,
                        query.size,
                        InetAddress.getByName("127.0.0.1"),
                        port,
                    ),
                )
                val buf = ByteArray(512)
                val resp = DatagramPacket(buf, buf.size)
                sock.receive(resp)
                assertEquals(0x12.toByte(), buf[0])
                assertEquals(0x34.toByte(), buf[1])
                assertEquals(3, buf[3].toInt() and 0x0F)
            }
        } finally {
            TelosDnsStub.stop()
            assertFalse(TelosDnsStub.isRunning())
        }
    }

    private fun freeUdpPort(): Int {
        DatagramSocket(0).use { return it.localPort }
    }

    private fun buildDnsQuery(id: Int, qname: String): ByteArray {
        val labels = qname.trim('.').split('.').filter { it.isNotEmpty() }
        val nameBytes = ArrayList<Byte>()
        for (label in labels) {
            val raw = label.toByteArray(Charsets.US_ASCII)
            nameBytes += raw.size.toByte()
            raw.forEach { nameBytes += it }
        }
        nameBytes += 0
        val out = ByteArray(12 + nameBytes.size + 4)
        out[0] = ((id ushr 8) and 0xFF).toByte()
        out[1] = (id and 0xFF).toByte()
        out[2] = 0x01 // RD
        out[3] = 0x00
        out[4] = 0x00
        out[5] = 0x01 // QDCOUNT = 1
        var i = 12
        for (b in nameBytes) out[i++] = b
        out[i++] = 0x00
        out[i++] = 0x01 // A
        out[i++] = 0x00
        out[i] = 0x01 // IN
        return out
    }
}
