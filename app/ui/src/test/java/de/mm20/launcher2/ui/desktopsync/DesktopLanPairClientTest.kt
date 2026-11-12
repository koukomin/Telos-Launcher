package de.mm20.launcher2.ui.desktopsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopLanPairClientTest {
    @Test
    fun parseUriRoundTrip() {
        val uri = DesktopLanPairClient.buildUri("ab23cd45", "192.168.1.42", 17890)
        val parsed = DesktopLanPairClient.parseUri(uri)
        assertNotNull(parsed)
        assertEquals("AB23CD45", parsed!!.code)
        assertEquals("192.168.1.42", parsed.host)
        assertEquals(17890, parsed.port)
        assertFalse(parsed.tls)
    }

    @Test
    fun parseUriRejectsInvalid() {
        assertNull(DesktopLanPairClient.parseUri(""))
        assertNull(DesktopLanPairClient.parseUri("https://example.com"))
        assertNull(DesktopLanPairClient.parseUri("telos-sync://pair?code=AB&host=10.0.0.1&port=1"))
    }

    @Test
    fun normalizeCodeStripsAmbiguous() {
        assertEquals("ABCD2345", DesktopLanPairClient.normalizeCode(" abcd2345 "))
        assertEquals("ABCD", DesktopLanPairClient.normalizeCode("AB0O1ICD")) // strips 0/O/1/I
    }
}
