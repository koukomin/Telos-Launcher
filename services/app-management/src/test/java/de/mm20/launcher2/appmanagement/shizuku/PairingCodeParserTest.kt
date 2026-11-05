package de.mm20.launcher2.appmanagement.shizuku

import org.junit.Assert.*
import org.junit.Test

class PairingCodeParserTest {
    @Test fun acceptsStandaloneCodesIncludingLeadingZeros() {
        assertEquals("012345", PairingCodeParser.extract(listOf("Wi-Fi pairing code", "012345")))
        assertEquals("012345", PairingCodeParser.extract(listOf(" 012 345 ")))
    }
    @Test fun neverBuildsACodeFromAddressesOrOtherLabels() {
        assertNull(PairingCodeParser.extract(listOf("192.168.12.34:12345", "123", "456")))
        assertNull(PairingCodeParser.extract(listOf("1234567", "12345", "IP address: 123456")))
    }
}
