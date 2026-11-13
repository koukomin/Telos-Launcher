package com.telos.desktop.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchedThemePacksTest {
    @Test
    fun documentsPapirusBreezexAndCandyBibata() {
        assertEquals(2, MatchedThemePacks.all.size)
        assertNotNull(MatchedThemePacks.forIconPackCatalogId("papirus"))
        assertNotNull(MatchedThemePacks.forIconPackCatalogId("candy-icons"))
        assertNotNull(MatchedThemePacks.forCursorCatalogId("breezex-dark"))
        assertNotNull(MatchedThemePacks.forCursorCatalogId("bibata-modern-classic"))
    }

    @Test
    fun papirusMatchesBreezex() {
        val pack = MatchedThemePacks.forIconPackCatalogId("papirus")!!
        assertEquals("papirus-breezex", pack.id)
        assertTrue(pack.cursorThemeFolders.any { it.startsWith("BreezeX") })
        assertEquals("Papirus-Dark", MatchedThemePacks.preferredIconTheme(pack))
        assertEquals("BreezeX-Dark", MatchedThemePacks.preferredCursorTheme(pack))
    }

    @Test
    fun candyIconsMatchBibataWithCandyCyberNote() {
        val pack = MatchedThemePacks.forIconPackCatalogId("candy-icons")!!
        assertEquals("candy-bibata", pack.id)
        assertTrue(pack.candyCyberChrome)
        assertTrue(pack.cursorThemeFolders.any { it.startsWith("Bibata") })
        assertEquals(pack, MatchedThemePacks.forIconThemeName("CandyCyber"))
    }

    @Test
    fun cursorFolderLookup() {
        assertEquals(
            MatchedThemePacks.papirusBreezex,
            MatchedThemePacks.forCursorThemeFolder("BreezeX-Dark"),
        )
        assertEquals(
            MatchedThemePacks.candyBibata,
            MatchedThemePacks.forCursorThemeFolder("Bibata-Modern-Classic"),
        )
    }
}
