package de.mm20.launcher2.comms.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

class TvExtraTest {
    private fun ch(id: String, name: String, vararg urls: String, country: String = "GR", alt: List<String> = emptyList()) =
        TvChannel(id, name, altNames = alt, country = country, streams = urls.map { TvStream(it) })

    @Test fun m3uExtrasParseIdAndName() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="ERT1.gr" tvg-name="ERT 1" tvg-logo="https://l.example/e.png" group-title="ΠΑΝΕΛΛΑΔΙΚΑ",ERT1 HD
            https://s.example/e1.mpd
            #EXTINF:-1 tvg-name="Only Name" group-title="Ειδήσεις",
            https://s.example/n.m3u8
        """.trimIndent()
        val e = TvM3u.parse(text).entries
        assertEquals(2, e.size)
        assertEquals("ERT1.gr", e[0].tvgId)
        assertEquals("ERT 1", e[0].tvgName)
        assertEquals("ERT1 HD", e[0].name)
        assertEquals("Only Name", e[1].name)
        assertEquals("", e[1].tvgId)
        assertEquals("Ειδήσεις", e[1].group)
    }

    @Test fun nameKeyIgnoresCaseAccentsSuffixAndGreeklish() {
        assertEquals(TvExtraMerge.nameKey("ERT1 HD"), TvExtraMerge.nameKey("ert1"))
        assertEquals(TvExtraMerge.nameKey("Σκάι"), TvExtraMerge.nameKey("SKAI TV"))
        assertEquals(TvExtraMerge.nameKey("Alpha (Greece)"), TvExtraMerge.nameKey("ALPHA"))
        assertTrue(TvExtraMerge.nameKey("ERT1") != TvExtraMerge.nameKey("ERT2"))
    }

    @Test fun categoriesFromGreekGroups() {
        assertEquals(listOf("news"), TvExtraMerge.categoriesFor("Ειδήσεις"))
        assertEquals(listOf("sports"), TvExtraMerge.categoriesFor("ΑΘΛΗΤΙΚΑ"))
        assertTrue(TvExtraMerge.categoriesFor("ΠΑΝΕΛΛΑΔΙΚΑ").isEmpty())
    }

    @Test fun mergeAddsStreamsAndExtraChannels() {
        val base = TvIndex(listOf(
            ch("ERT1.gr", "ERT1", "https://iptv.example/ert1.m3u8"),
            ch("SKAI.gr", "Σκάι", "https://iptv.example/skai.m3u8"),
            ch("BBC.uk", "BBC One", "https://iptv.example/bbc.m3u8", country = "GB"),
        ))
        val free = TvExtraMerge.Source(TvExtraMerge.LABEL_FREE_TV, listOf(
            TvM3uEntry("ERT 1", "https://free.example/ert1.mpd", "", "", tvgId = "ERT1.gr"),
            TvM3uEntry("Unknown One", "https://free.example/u1.m3u8", "https://l.example/u.png", "Ειδήσεις"),
            TvM3uEntry("Bad", "https://127.0.0.1/x.m3u8", "", ""),
        ))
        val gtv = TvExtraMerge.Source(TvExtraMerge.LABEL_GREEKTV, listOf(
            TvM3uEntry("SKAI HD", "https://g.example/skai.m3u8", "", "ΠΑΝΕΛΛΑΔΙΚΑ"),
            TvM3uEntry("Unknown One HD", "https://g.example/u1.m3u8", "", ""),
        ))
        val m = TvExtraMerge.merge(base, TvExtraMerge.Extras(listOf(free, gtv)), emptyMap(), 0L)
        val ert = m.channel("ERT1.gr")!!
        assertEquals(listOf("https://iptv.example/ert1.m3u8", "https://free.example/ert1.mpd"), ert.streams.map { it.url })
        assertEquals("Free-TV", ert.streams[1].source)
        assertFalse(ert.isExtra)
        assertEquals(2, m.channel("SKAI.gr")!!.streams.size)
        assertEquals(1, m.channel("BBC.uk")!!.streams.size)
        val extras = m.all.filter { it.isExtra }
        assertEquals(1, extras.size)
        val x = extras[0]
        assertTrue(x.id.startsWith("extra:"))
        assertEquals("GR", x.country)
        assertEquals(listOf("news"), x.categories)
        assertEquals(2, x.streams.size)
        assertEquals("https://l.example/u.png", x.logoUrl)
        assertEquals(base.size + 1, m.size)
    }

    @Test fun extraChannelHiddenWhenAllStreamsBad() {
        val base = TvIndex(listOf(ch("A.gr", "Alpha", "https://i.example/a.m3u8")))
        val src = TvExtraMerge.Source("greektvm3u", listOf(TvM3uEntry("Zeta Local", "https://g.example/z.m3u8", "", "")))
        val bad = mapOf("https://g.example/z.m3u8" to 1000L)
        val m = TvExtraMerge.merge(base, TvExtraMerge.Extras(listOf(src)), bad, 2000L)
        assertEquals(1, m.size)
        assertEquals(2, TvExtraMerge.merge(base, TvExtraMerge.Extras(listOf(src)), emptyMap(), 2000L).size)
    }

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="ERT1.gr"><display-name>ERT1</display-name></channel>
          <channel id="x2"><display-name lang="el">Σκάι</display-name></channel>
          <channel id="other"><display-name>Other</display-name></channel>
          <programme start="20240601120000 +0200" stop="20240601130000 +0200" channel="ERT1.gr">
            <title lang="el">News</title><desc>Long text</desc><category>News</category>
          </programme>
          <programme start="20240601110000 +0000" stop="20240601120000 +0000" channel="ERT1.gr"><title>Next show</title></programme>
          <programme start="20240601100000 +0000" stop="20240601110000 +0000" channel="x2"><title>Skai now</title></programme>
          <programme start="20240601100000 +0000" stop="20240601110000 +0000" channel="other"><title>Ignored</title></programme>
          <programme start="20240101100000 +0000" stop="20240101110000 +0000" channel="ERT1.gr"><title>Too old</title></programme>
        </tv>
    """.trimIndent()

    private val resolver = { id: String, names: List<String> ->
        when {
            id == "ERT1.gr" -> "ERT1.gr"
            names.any { TvExtraMerge.nameKey(it) == TvExtraMerge.nameKey("SKAI") } -> "SKAI.gr"
            else -> null
        }
    }

    @Test fun xmltvParsingAndTimezones() {
        assertEquals(1717236000000L, XmltvParser.parseTime("20240601120000 +0200"))
        assertEquals(1717243200000L, XmltvParser.parseTime("20240601120000 +0000"))
        assertEquals(1717243200000L, XmltvParser.parseTime("20240601120000"))
        assertEquals(1717250400000L, XmltvParser.parseTime("20240601120000 -0200"))
        assertNull(XmltvParser.parseTime("garbage"))

        val now = XmltvParser.parseTime("20240601104500 +0000")!!
        val p = KXmlParser().apply { setInput(StringReader(xml)) }
        val r = XmltvParser.parse(p, now, resolver)
        assertEquals(setOf("ERT1.gr", "SKAI.gr"), r.keys)
        assertEquals(2, r["ERT1.gr"]!!.size)
    }

    @Test fun nowNext() {
        val now0 = XmltvParser.parseTime("20240601104500 +0000")!!
        val p = KXmlParser().apply { setInput(StringReader(xml)) }
        val r = XmltvParser.parse(p, now0, resolver)
        val ert = r["ERT1.gr"]!!
        // 10:45 UTC: News (12:00 +0200 = 10:00-11:00 UTC) is on, "Next show" (11:00-12:00 UTC) follows
        val a = ert.nowNext(now0)!!
        assertEquals("News", a.current!!.title)
        assertEquals("Long text", a.current!!.description)
        assertEquals("News", a.current!!.category)
        assertEquals("Next show", a.next!!.title)
        // 11:30 UTC: "Next show" is on, nothing follows
        val b = ert.nowNext(now0 + 45 * 60_000L)!!
        assertEquals("Next show", b.current!!.title)
        assertNull(b.next)
        // 09:30 UTC: nothing on yet, News is next
        val c = ert.nowNext(now0 - 75 * 60_000L)!!
        assertNull(c.current)
        assertEquals("News", c.next!!.title)
        assertNull(ert.nowNext(now0 + 24 * 3600_000L))
        assertEquals("Skai now", r["SKAI.gr"]!!.nowNext(now0)!!.current!!.title)
    }

    @Test fun descriptionIsCapped() {
        val long = "x".repeat(1000)
        val x = """<tv><programme start="20240601100000 +0000" stop="20240601110000 +0000" channel="a"><title>T</title><desc>$long</desc></programme></tv>"""
        val now = XmltvParser.parseTime("20240601103000 +0000")!!
        val r = XmltvParser.parse(KXmlParser().apply { setInput(StringReader(x)) }, now) { _, _ -> "A" }
        assertEquals(300, r["A"]!!.nowNext(now)!!.current!!.description.length)
    }

    @Test fun brokenXmlKeepsWhatWasRead() {
        val x = """<tv><programme start="20240601100000 +0000" stop="20240601110000 +0000" channel="a"><title>T</title></programme><programme start="""
        val now = XmltvParser.parseTime("20240601103000 +0000")!!
        val r = XmltvParser.parse(KXmlParser().apply { setInput(StringReader(x)) }, now) { _, _ -> "A" }
        assertEquals(1, r["A"]!!.size)
    }
}
