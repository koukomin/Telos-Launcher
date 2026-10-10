package de.mm20.launcher2.comms.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvCatalogParserTest {
    private val channels = """[
        {"id":"ERT1.gr","name":"ERT1","alt_names":["ΕΡΤ1","ERT 1"],"country":"GR","categories":["general"],"is_nsfw":false,"closed":null,"replaced_by":null,"extra":{"x":1}},
        {"id":"Old.gr","name":"Old","country":"GR","categories":[],"is_nsfw":false,"closed":"2020-01-01"},
        {"id":"Moved.gr","name":"Moved","country":"GR","categories":[],"is_nsfw":false,"replaced_by":"ERT1.gr"},
        {"id":"Adult.us","name":"Adult","country":"US","categories":["xxx"],"is_nsfw":true},
        {"id":"Blocked.us","name":"Blocked","country":"US","categories":[],"is_nsfw":false},
        {"id":"NoStream.us","name":"NoStream","country":"US","categories":[]},
        {"id":"BBC.uk","name":"BBC One","country":"UK","categories":["general","news"],"is_nsfw":false},
        {"id":"Weird.xx","name":42,"country":["x"]},
        "not an object",
        {"id":"Lan.us","name":"Lan","country":"US","categories":[]}
    ]"""
    private val streams = """[
        {"channel":"ERT1.gr","feed":null,"title":"ERT1","url":"https://a.example/ert1_480.m3u8","quality":"480p","referrer":null,"user_agent":null},
        {"channel":"ERT1.gr","feed":null,"title":"ERT1","url":"https://a.example/ert1_1080.m3u8","quality":"1080p","referrer":"https://ert.gr/","user_agent":"UA"},
        {"channel":"ERT1.gr","url":"https://a.example/ert1_1080.m3u8","quality":"1080p"},
        {"channel":"Old.gr","url":"https://a.example/old.m3u8"},
        {"channel":"Moved.gr","url":"https://a.example/moved.m3u8"},
        {"channel":"Adult.us","url":"https://a.example/adult.m3u8"},
        {"channel":"Blocked.us","url":"https://a.example/blocked.m3u8"},
        {"channel":"BBC.uk","url":"http://b.example/bbc.m3u8","quality":"720p"},
        {"channel":"Lan.us","url":"http://192.168.1.5/live.m3u8"},
        {"channel":"Bad.us","url":"ftp://x/y"},
        {"channel":null,"url":"https://a.example/orphan.m3u8"}
    ]"""
    private val logos = """[
        {"channel":"ERT1.gr","feed":null,"tags":[],"width":512,"height":512,"format":"PNG","url":"https://l.example/ert.png"},
        {"channel":"ERT1.gr","feed":null,"tags":[],"width":512,"height":512,"format":"SVG","url":"https://l.example/ert.svg"}
    ]"""
    private val feeds = """[
        {"channel":"ERT1.gr","id":"SD","is_main":false,"languages":["eng"]},
        {"channel":"ERT1.gr","id":"HD","is_main":true,"languages":["ell"]}
    ]"""
    private val blocklist = """[{"channel":"Blocked.us","ref":"https://x"}]"""
    private val categories = """[{"id":"news","name":"News"},{"id":"general","name":"General"}]"""

    private fun index() = TvCatalogParser.fromStrings(channels, streams, logos, feeds, blocklist, categories)

    @Test fun filtersExcludedChannels() {
        val i = index()
        val ids = i.all.map { it.id }.toSet()
        assertEquals(setOf("ERT1.gr", "BBC.uk"), ids)
    }

    @Test fun streamsAreSortedDeduplicatedAndFiltered() {
        val c = index().channel("ERT1.gr")!!
        assertEquals(listOf("https://a.example/ert1_1080.m3u8", "https://a.example/ert1_480.m3u8"), c.streams.map { it.url })
        assertEquals("UA", c.streams[0].userAgent)
        assertEquals("https://ert.gr/", c.streams[0].referrer)
    }

    @Test fun logoLanguagesAndNames() {
        val c = index().channel("ERT1.gr")!!
        assertEquals("https://l.example/ert.png", c.logoUrl)
        assertEquals(listOf("ell", "eng"), c.languages)
        assertEquals(listOf("ΕΡΤ1", "ERT 1"), c.altNames)
    }

    @Test fun ukBecomesGb() {
        val i = index()
        assertEquals("GB", i.channel("BBC.uk")!!.country)
        assertEquals(1, i.byCountry(listOf("UK")).size)
        assertEquals(1, i.byCountry(listOf("gb")).size)
    }

    @Test fun queries() {
        val i = index()
        assertEquals(listOf("ERT1.gr"), i.byCountry(listOf("GR")).map { it.id })
        assertEquals(listOf("ERT1.gr"), i.byLanguage(listOf("el")).map { it.id })
        assertEquals(listOf("BBC.uk"), i.byCategory("news").map { it.id })
        assertEquals("News", i.categories().first { it.id == "news" }.name)
        assertEquals(2, i.countries().size)
        assertEquals("GR", i.countries().first { it.code == "GR" }.code)
    }

    @Test fun searchUsesGreekAndGreeklish() {
        val i = index()
        assertEquals("ERT1.gr", i.search("ερτ").firstOrNull()?.id)
        assertEquals("ERT1.gr", i.search("ert").firstOrNull()?.id)
        assertEquals("BBC.uk", i.search("bbc").firstOrNull()?.id)
        assertTrue(i.search("   ").isEmpty())
    }

    @Test fun suggestions() {
        val i = index()
        assertEquals("ERT1.gr", i.suggestedForLocale("GR", "el").first().id)
        assertTrue(i.suggestedForLocale("", "xx").isEmpty())
    }

    @Test fun flag() {
        assertEquals("🇬🇷", TvIndex.flagEmoji("GR"))
        assertEquals("", TvIndex.flagEmoji("X1"))
    }

    @Test fun garbageNeverThrows() {
        assertEquals(0, TvCatalogParser.fromStrings("not json", "{}", "[", null, "42").size)
        assertEquals(0, TvCatalogParser.fromStrings(null, null).size)
        assertNull(TvCatalogParser.fromStrings("[]", "[]").channel("x"))
    }

    @Test fun schemaDriftIsTolerated() {
        val ch = """[{"id":"A.gr","name":"A","country":"GR","categories":"news","alt_names":null,"is_nsfw":"no","new_field":[1,2]}]"""
        val st = """[{"channel":"A.gr","url":"https://x.example/a.m3u8","quality":720,"extra":true}]"""
        val c = TvCatalogParser.fromStrings(ch, st).channel("A.gr")
        assertNotNull(c)
        assertEquals("", c!!.streams[0].quality)
    }
}
