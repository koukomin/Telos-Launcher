package de.mm20.launcher2.comms.blocklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** The formats of the maintained IP lists that are offered as presets, and the preset data itself */
class IpListFormatsTest {
    private fun utc(s: String): Long = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)!!.time
    private fun parse(text: String, skip: Boolean = false) = BlockListParser.parseIpRangesInfo(text.reader().buffered(), skip)

    private val fireholSample = """
        #
        # firehol_level1
        #
        # ipv4 hash:net ipset
        #
        # Maintainer      : FireHOL
        # Source File Date: Thu Oct  8 04:14:54 UTC 2026
        # This File Date  : Thu Oct  8 04:41:57 UTC 2026
        # Update Frequency: 1 min
        # Entries         : 3 subnets, 600 unique IPs
        #
        0.0.0.0/8
        1.10.16.0/20
        10.0.0.0/8
        2.26.75.0/24
        8.8.8.8
    """.trimIndent()

    @Test fun fireholNetsetWithCommentsAndHeaderDate() {
        val r = parse(fireholSample)
        assertEquals(utc("2026-10-08 04:41:57"), r.dataDate)
        assertEquals(5, r.count)
        assertEquals(0x00000000L, r.ranges[0])
        assertEquals(0x00FFFFFFL, r.ranges[1])
    }

    @Test fun sourceFileDateIsNotTheListDate() {
        assertNull(DataDates.parseHeaderLine("# Source File Date: Thu Oct  8 04:14:54 UTC 2026"))
        assertNotNull(DataDates.parseHeaderLine("# This File Date  : Thu Oct  8 04:41:57 UTC 2026"))
    }

    @Test fun reservedRangesAreCutOut() {
        val r = parse(fireholSample, skip = true)
        // 10.0.0.0/8 is gone, the rest stays
        assertEquals(4, r.count)
        assertFalse(r.ranges.toList().chunked(2).any { (a, b) -> 0x0A000001L in a..b })
    }

    @Test fun bigRangeKeepsTheOutsideParts() {
        // 8.0.0.0 - 12.255.255.255 contains 10.0.0.0/8
        val pieces = BlockListParser.subtractReserved(0x08000000L, 0x0CFFFFFFL)
        assertEquals(listOf(0x08000000L to 0x09FFFFFFL, 0x0B000000L to 0x0CFFFFFFL), pieces)
        assertTrue(BlockListParser.subtractReserved(0xC0A80101L, 0xC0A80101L).isEmpty())
        assertEquals(listOf(0x08080808L to 0x08080808L), BlockListParser.subtractReserved(0x08080808L, 0x08080808L))
    }

    @Test fun spamhausDropLines() {
        val text = "; Spamhaus DROP List 2026/10/08 - (c) 2026 The Spamhaus Project SLU\n; https://www.spamhaus.org/drop/drop.txt\n" +
            "; Last-Modified: Wed, 07 Oct 2026 17:01:40 GMT\n; Expires: Thu, 08 Oct 2026 12:14:02 GMT\n" +
            "1.10.16.0/20 ; SBL256894\n1.19.0.0/16 ; SBL434604\n"
        val r = parse(text)
        assertEquals(utc("2026-10-07 17:01:40"), r.dataDate)
        assertEquals(2, r.count)
        assertEquals(0x010A1000L, r.ranges[0])
        assertEquals(0x010A1FFFL, r.ranges[1])
    }

    @Test fun abuseChHeaderDate() {
        assertEquals(utc("2026-03-04 14:28:39"), DataDates.parseHeaderLine("# Last updated: 2026-03-04 14:28:39 UTC                        #".trimEnd('#', ' ')))
    }

    @Test fun dshieldTabSeparatedLines() {
        val text = "#    updated: 2026-10-08T11:15:59.445859\n#    (1) start of netblock\n" +
            "64.62.156.0\t64.62.156.255\t24\t354\tHURRICANE\tUS\tabuse@he.net\n"
        val r = parse(text)
        assertEquals(utc("2026-10-08 11:15:59"), r.dataDate)
        assertEquals(listOf(0x403E9C00L, 0x403E9CFFL), r.ranges.toList())
    }

    @Test fun plainIpsAndIpv6AreHandled() {
        val r = parse("1.2.3.4\n2001:db8::/32\n5.6.7.8 # note\n::1\n")
        assertEquals(listOf(0x01020304L, 0x01020304L, 0x05060708L, 0x05060708L), r.ranges.toList())
    }

    @Test fun p2pAndIpfilterStillWorkWithTheNewCommentRules() {
        assertEquals(0x01020304L to 0x01020310L, BlockListParser.parseIpLine("Some; name # x:1.2.3.4-1.2.3.16"))
        assertEquals(0x01020304L to 0x01020310L, BlockListParser.parseIpLine("001.002.003.004 - 001.002.003.016 , 000 , Name; x"))
    }

    @Test fun gzipHeaderDate() {
        val head = byteArrayOf(0x1f, 0x8b.toByte(), 8, 0, 0x0F, 0x00, 0x00, 0x00)
        assertEquals(15_000L, BlockListParser.gzipDate(head))
        assertEquals(0L, BlockListParser.gzipDate(byteArrayOf(0x1f, 0x8b.toByte(), 8, 0, 0, 0, 0, 0)))
    }

    @Test fun openTextReportsTheGzipDate() {
        val gz = java.io.ByteArrayOutputStream().also { o -> java.util.zip.GZIPOutputStream(o).use { it.write("1.2.3.4\n".toByteArray()) } }.toByteArray()
        // set a modification time in the header
        gz[4] = 0x10; gz[5] = 0; gz[6] = 0; gz[7] = 0
        var seen = -1L
        val text = BlockListParser.openText(gz.inputStream(), 1000) { seen = it }.readText()
        assertEquals("1.2.3.4\n", text)
        assertEquals(16_000L, seen)
    }

    @Test fun torrentPresetsAreSane() {
        val ip = BlockListPresets.all.filter { it.kind == BlockListKind.TORRENT_IP }
        assertTrue(ip.size >= 3)
        assertEquals(BlockListPresets.all.size, BlockListPresets.all.map { it.id }.toSet().size)
        for (p in ip) {
            assertTrue(p.id, p.url.startsWith("https://"))
            assertTrue(p.id, p.license.isNotBlank())
            assertTrue(p.id, p.homepage.startsWith("https://"))
            assertTrue(p.id, p.description.isNotBlank())
        }
        // lists that contain private ranges must not block the local network
        assertTrue(BlockListPresets.byId("ip-firehol-level1")!!.skipReserved)
        assertTrue(BlockListPresets.byId("ip-spamhaus-drop") != null)
        assertTrue(BlockListPresets.byId("ip-naunter") != null)
    }
}
