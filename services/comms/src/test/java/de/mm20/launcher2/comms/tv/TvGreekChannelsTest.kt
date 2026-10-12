package de.mm20.launcher2.comms.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TvGreekChannelsTest {
    private val today = LocalDate.of(2026, 10, 10)

    private fun entry(name: String, url: String, tvgId: String = "", group: String = "") =
        TvM3uEntry(name, url, "", group, tvgId = tvgId)

    @Test fun activationOnFreshInstall() {
        val el = Locale("el", "GR")
        assertTrue(TvSettings.greeceActive(emptyList(), "", el))
        assertTrue(TvSettings.greeceActive(emptyList(), "GR", Locale.US))
        assertTrue(TvSettings.greeceActive(emptyList(), "", Locale("el")))
        assertTrue(TvSettings.greeceActive(emptyList(), "", Locale("en", "GR")))
        assertFalse(TvSettings.greeceActive(emptyList(), "DE", Locale.GERMANY))
        assertFalse(TvSettings.greeceActive(listOf("DE"), "GR", el))
        assertTrue(TvSettings.greeceActive(listOf("DE", "GR"), "", Locale.US))
    }

    @Test fun defaultCountryFallsBackToLocale() {
        assertEquals("GR", TvSettings.defaultCountry("", Locale("el")))
        assertEquals("DE", TvSettings.defaultCountry("", Locale.GERMANY))
        assertEquals("FR", TvSettings.defaultCountry("fr", Locale.US))
        assertEquals("", TvSettings.defaultCountry("", Locale("en")))
    }

    @Test fun closedAndReplacedRules() {
        assertFalse(TvCatalogParser.isClosed(null, today))
        assertTrue(TvCatalogParser.isClosed("2019-03-01", today))
        assertFalse(TvCatalogParser.isClosed("2027-01-01", today))
        val channels = """[
          {"id":"Skai.gr","name":"SKAI","country":"GR","closed":"2027-01-01"},
          {"id":"OpenBeyond.gr","name":"Open","country":"GR","replaced_by":"Open2.gr"},
          {"id":"Old.gr","name":"Old","country":"GR","closed":"2015-01-01"},
          {"id":"Mega.gr","name":"Mega Channel","country":"GR","replaced_by":"Nope.gr"},
          {"id":"Ant1.gr","name":"ANT1","country":"GR","is_nsfw":false,"closed":null}
        ]"""
        val streams = """[
          {"channel":"Skai.gr","url":"https://a.example/skai.m3u8"},
          {"channel":"OpenBeyond.gr","url":"https://a.example/open.m3u8"},
          {"channel":"Open2.gr","url":"https://a.example/open2.m3u8"},
          {"channel":"Old.gr","url":"https://a.example/old.m3u8"},
          {"channel":"Mega.gr","url":"https://a.example/mega.m3u8"},
          {"channel":"Ant1.gr","url":"https://a.example/ant1.m3u8"},
          {"channel":"Ant1.gr","url":"http://127.0.0.1/x.m3u8"}
        ]"""
        val (idx, stats) = TvCatalogParser.parseWithStats(
            TvCatalogParser.Sources({ channels.byteInputStream() }, { streams.byteInputStream() }), today,
        )
        assertNotNull(idx.channel("Skai.gr"))
        assertNotNull(idx.channel("Mega.gr"))
        assertNotNull(idx.channel("Ant1.gr"))
        assertEquals(1, idx.channel("Ant1.gr")!!.streams.size)
        assertEquals(null, idx.channel("Old.gr"))
        assertEquals(null, idx.channel("OpenBeyond.gr"))
        assertEquals(1, stats.closed)
        assertEquals(1, stats.replaced)
        assertEquals(3, stats.kept)
        assertTrue(stats.toString().contains("kept=3"))
    }

    @Test fun extrasCreateBigGreekChannelsWhenCatalogLacksThem() {
        val base = TvIndex(listOf(TvChannel("ERT1.gr", "ERT1", country = "GR", streams = listOf(TvStream("https://i.example/e.m3u8")))))
        val free = TvExtraMerge.Source(TvExtraMerge.LABEL_FREE_TV, listOf(
            entry("Skai TV", "http://skai-live.siliconweb.com/media/cambria4/index.m3u8", "SkaiTV.gr", "Greece"),
            entry("Open TV", "https://liveopen.siliconweb.com/openTvLive/liveopen/playlist.m3u8", "OpenTV.gr", "Greece"),
            entry("Star", "https://livestar.siliconweb.com/starvod/star4/star4.m3u8", "StarChannel.gr", "Greece"),
            entry("ANT1", "https://mcdn.antennaplus.gr/live/media0/Ant1/HLS/Ant1.m3u8", "ANT1.gr", "Greece"),
            entry("AlphaTV", "https://alphatvlive2.siliconweb.com/alphatvlive/live_abr/playlist.m3u8", "AlphaTV.gr", "Greece"),
            entry("Mega News", "https://m.example/meganews.m3u8", "MegaChannel.gr", "Greece"),
        ))
        val gtv = TvExtraMerge.Source(TvExtraMerge.LABEL_GREEKTV, listOf(
            entry("SKAI HD", "https://skai-live.siliconweb.com/media/cambria4/index.m3u8", "", "ΠΑΝΕΛΛΑΔΙΚΑ"),
            entry("ALPHA HD", "https://alphatvlive.siliconweb.com/alphatvlive/live_abr/playlist.m3u8", "", "ΠΑΝΕΛΛΑΔΙΚΑ"),
            entry("MEGA COSMOS", "http://cdn.smart-tv-data.com/mega/MegaCosmos/mpeg.2ts", "", "WEB TV"),
        ))
        val m = TvExtraMerge.merge(base, TvExtraMerge.Extras(listOf(free, gtv)), emptyMap(), 0L)
        val names = m.all.map { it.name }
        for (n in listOf("Skai TV", "Open TV", "Star", "ANT1", "AlphaTV", "Mega News", "Mega Cosmos")) {
            assertTrue("missing $n in $names", n in names)
        }
        assertTrue(m.all.filter { it.name != "ERT1" }.all { it.isExtra && it.country == "GR" })
        // "SKAI HD" and "Skai TV" are one channel with two streams (the first one differs by https)
        assertEquals(2, m.all.first { it.name == "Skai TV" }.streams.size)
        val found = m.search("mega").map { it.name }
        assertTrue(found.containsAll(listOf("Mega News", "Mega Cosmos")))
    }

    @Test fun streamKnownOnlyInForeignChannelDoesNotHideGreekChannel() {
        val url = "https://shared.example/skai.m3u8"
        val base = TvIndex(listOf(TvChannel("Skai.cy", "Other", country = "CY", streams = listOf(TvStream(url)))))
        val src = TvExtraMerge.Source("Free-TV", listOf(entry("Skai TV", url)))
        val m = TvExtraMerge.merge(base, TvExtraMerge.Extras(listOf(src)), emptyMap(), 0L)
        assertTrue(m.all.any { it.isExtra && it.name == "Skai TV" })
    }

    @Test fun prettyNames() {
        assertEquals("Mega Cosmos", TvExtraMerge.prettyName("MEGA COSMOS"))
        assertEquals("Open TV HD", TvExtraMerge.prettyName("OPEN TV HD"))
        assertEquals("ANT1", TvExtraMerge.prettyName("ANT1"))
        assertEquals("Mega News", TvExtraMerge.prettyName("Mega News"))
    }
}
