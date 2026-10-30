package com.telos.desktop.search

import com.telos.desktop.apps.DesktopEntry
import com.telos.desktop.apps.TelosAppCategory
import com.telos.desktop.apps.TelosVirtualApp
import com.telos.desktop.icons.CandyIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopSearchTest {
    @Test
    fun ranksPrefixAboveContains() {
        assertTrue(DesktopSearch.rank("fi", "Files") > DesktopSearch.rank("fi", "Config"))
        assertEquals(0, DesktopSearch.rank("zz", "Files"))
    }

    @Test
    fun findsTelosAndDesktop() {
        val telos = listOf(
            TelosVirtualApp("telos_files_app", "Files", TelosAppCategory.Utility, CandyIcon.Folder, "Files"),
        )
        val desk = listOf(
            DesktopEntry(
                file = File("/usr/share/applications/firefox.desktop"),
                name = "Firefox",
                comment = "Web Browser",
                icon = "firefox",
                exec = "firefox %u",
                path = null,
                terminal = false,
                noDisplay = false,
                categories = listOf("Network"),
                telosCategory = TelosAppCategory.Network,
            ),
        )
        val hits = DesktopSearch.search("fi", apps = desk, filesRoot = emptyList(), telos = telos)
        assertTrue(hits.any { it.kind == SearchKind.TelosApp && it.title == "Files" })
        assertTrue(hits.any { it.kind == SearchKind.Desktop && it.title == "Firefox" })
    }
}
