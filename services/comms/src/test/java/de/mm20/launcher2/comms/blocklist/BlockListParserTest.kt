package de.mm20.launcher2.comms.blocklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BlockListParserTest {
    private fun domains(text: String) = BlockListParser.parseDomains(text.reader().buffered())

    @Test fun hostsFormat() {
        val d = domains("# comment\r\n127.0.0.1 localhost\r\n0.0.0.0 Ads.Example.com # tail\r\n0.0.0.0 0.0.0.0\r\n\r\n::1 ip6-localhost\r\n")
        assertEquals(setOf("ads.example.com"), d)
    }

    @Test fun plainAndWildcard() {
        assertEquals(setOf("a.example.com", "b.example.org"), domains("a.example.com\n*.b.example.org\nnodot\n"))
    }

    @Test fun adblockDomainRulesOnly() {
        val d = domains(
            "[Adblock Plus 2.0]\n! c\n||ads.example.com^\n||t.example.net^\$third-party\n" +
                "||x.example.com/path\n||y.example.com^\$script\n@@||ok.example.com^\n##.banner\nexample.com##.x\n/regex/\n"
        )
        assertEquals(setOf("ads.example.com", "t.example.net"), d)
    }

    @Test fun domainSetMatchesParents() {
        val s = DomainSet.of(listOf("example.com", "ads.other.net"))
        assertTrue(s.matches("example.com"))
        assertTrue(s.matches("Sub.Deep.example.com"))
        assertTrue(s.matches("ads.other.net"))
        assertFalse(s.matches("other.net"))
        assertFalse(s.matches("notexample.com"))
        assertFalse(s.matches("com"))
        assertFalse(DomainSet.EMPTY.matches("example.com"))
    }

    @Test fun p2pFormat() {
        assertEquals(0x01020304L to 0x01020310L, BlockListParser.parseIpLine("Some: name, Inc.:1.2.3.4-1.2.3.16"))
        assertEquals(0x0A000001L to 0x0A000001L, BlockListParser.parseIpLine("\"\"\"X\"\"\":10.0.0.1-10.0.0.1"))
        assertNull(BlockListParser.parseIpLine("# comment"))
    }

    @Test fun ipfilterDatFormat() {
        assertEquals(0x01020304L to 0x01020310L, BlockListParser.parseIpLine("001.002.003.004 - 001.002.003.016 , 000 , Name: x"))
    }

    @Test fun cidrAndSingle() {
        assertEquals(0xC0A80000L to 0xC0A800FFL, BlockListParser.parseIpLine("192.168.0.0/24"))
        assertEquals(0x08080808L to 0x08080808L, BlockListParser.parseIpLine("8.8.8.8"))
        assertNull(BlockListParser.parseIpLine("300.1.1.1"))
        assertNull(BlockListParser.parseIpLine("1.2.3.4/40"))
    }

    @Test fun rangesAreMerged() {
        val r = BlockListParser.parseIpRanges("1.0.0.0-1.0.0.10\n1.0.0.5-1.0.0.20\n1.0.0.21-1.0.0.30\n9.9.9.9\n".reader().buffered())
        assertEquals(listOf(0x01000000L, 0x0100001EL, 0x09090909L, 0x09090909L), r.toList())
    }

    @Test fun gzipAndZipAndPlain() {
        val text = "0.0.0.0 a.example.com\n"
        val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(text.toByteArray()) } }.toByteArray()
        val zip = ByteArrayOutputStream().also { o ->
            ZipOutputStream(o).use { z -> z.putNextEntry(ZipEntry("l.txt")); z.write(text.toByteArray()); z.closeEntry() }
        }.toByteArray()
        for (bytes in listOf(gz, zip, text.toByteArray())) {
            val d = BlockListParser.parseDomains(BlockListParser.openText(bytes.inputStream(), 1000))
            assertEquals(setOf("a.example.com"), d)
        }
    }

    @Test fun sizeLimit() {
        val big = ByteArray(5000) { 'a'.code.toByte() }
        val r = BlockListParser.openText(big.inputStream(), 1000)
        try { r.readText(); throw AssertionError("expected failure") } catch (e: java.io.IOException) { assertTrue(true) }
    }
}
