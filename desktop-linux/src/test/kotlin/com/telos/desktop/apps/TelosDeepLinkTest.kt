package com.telos.desktop.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelosDeepLinkTest {
    @Test
    fun parsesAppAliasAndDomain() {
        val net = TelosDeepLinkParser.parse("telos://network")
        assertTrue(net is TelosDeepLink.OpenApp)
        assertEquals("Network", (net as TelosDeepLink.OpenApp).windowTitle)

        val store = TelosDeepLinkParser.parse("telos://app/store")
        assertTrue(store is TelosDeepLink.OpenApp)
        assertEquals("Store", (store as TelosDeepLink.OpenApp).windowTitle)

        val files = TelosDeepLinkParser.parse("telos://app/telos_files_app")
        assertTrue(files is TelosDeepLink.OpenApp)
        assertEquals("Files", (files as TelosDeepLink.OpenApp).windowTitle)
    }

    @Test
    fun parsesSearchAndFilesPath() {
        val search = TelosDeepLinkParser.parse("telos://search?q=firefox")
        assertTrue(search is TelosDeepLink.Search)
        assertEquals("firefox", (search as TelosDeepLink.Search).query)

        val open = TelosDeepLinkParser.parse("telos://files?path=%2Ftmp%2Fdemo")
        assertTrue(open is TelosDeepLink.OpenFiles)
        assertEquals("/tmp/demo", (open as TelosDeepLink.OpenFiles).path?.path)
    }

    @Test
    fun parseArgsSupportsOpenFlag() {
        val link = TelosDeepLinkParser.parseArgs(arrayOf("--session", "--open", "telos://settings"))
        assertTrue(link is TelosDeepLink.OpenApp)
        assertEquals("Settings", (link as TelosDeepLink.OpenApp).windowTitle)
    }

    @Test
    fun busOffersAndTakes() {
        TelosDeepLinkBus.offer(TelosDeepLink.Search("abc"))
        assertEquals("abc", (TelosDeepLinkBus.take() as TelosDeepLink.Search).query)
        assertEquals(null, TelosDeepLinkBus.take())
    }

    @Test
    fun desktopEntryMentionsMime() {
        val entry = TelosDeepLinkRegistrar.desktopEntry("java -jar /opt/telos-desktop/telos-desktop.jar --open %u")
        assertTrue(entry.contains("x-scheme-handler/telos"))
        assertTrue(entry.contains("NoDisplay=true"))
    }
}
