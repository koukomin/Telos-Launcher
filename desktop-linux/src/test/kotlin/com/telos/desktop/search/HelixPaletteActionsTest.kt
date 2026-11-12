package com.telos.desktop.search

import com.telos.desktop.apps.DesktopEntry
import com.telos.desktop.apps.TelosAppCategory
import com.telos.desktop.apps.TelosVirtualApp
import com.telos.desktop.icons.CandyIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class HelixPaletteActionsTest {

    private val telos = listOf(
        TelosVirtualApp("telos_files_app", "Files", TelosAppCategory.Utility, CandyIcon.Folder, "Files"),
        TelosVirtualApp("telos_settings", "Settings", TelosAppCategory.Settings, CandyIcon.Settings, "Settings"),
        TelosVirtualApp("telos_network_app", "Network", TelosAppCategory.Network, CandyIcon.Network, "Network"),
        TelosVirtualApp("telos_calculator_app", "Calculator", TelosAppCategory.Utility, CandyIcon.Calculator, "Calculator"),
    )

    private val firefox = DesktopEntry(
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
    )

    @Test
    fun emptyQueryShowsBuiltinsAndWindowsFirst() {
        val items = HelixPaletteActions.build(
            query = "",
            windows = listOf(HelixPaletteWindow(3, "Notes")),
            telos = telos,
            apps = listOf(firefox),
            hasFocusedWindow = true,
            hasVaultClip = true,
        )
        assertTrue(items.any { it.commandId == HelixPaletteActions.CMD_FILES })
        assertTrue(items.any { it.commandId == HelixPaletteActions.CMD_SETTINGS })
        assertTrue(items.any { it.commandId == HelixPaletteActions.CMD_NETWORK })
        assertTrue(items.any { it.commandId == HelixPaletteActions.CMD_KILL })
        assertTrue(items.any { it.commandId == HelixPaletteActions.CMD_PASTE_VAULT })
        assertTrue(items.any { it.kind == HelixPaletteKind.Window && it.title == "Notes" })
        // Empty query must not dump every .desktop entry.
        assertFalse(items.any { it.kind == HelixPaletteKind.Desktop })
        val winScore = items.first { it.kind == HelixPaletteKind.Window }.score
        val filesScore = items.first { it.commandId == HelixPaletteActions.CMD_FILES }.score
        assertTrue(winScore >= filesScore)
    }

    @Test
    fun filtersAndRanksPrefixAboveContains() {
        val items = HelixPaletteActions.build(
            query = "fi",
            windows = listOf(HelixPaletteWindow(1, "Notes"), HelixPaletteWindow(2, "Files")),
            telos = telos,
            apps = listOf(firefox),
            hasFocusedWindow = false,
            hasVaultClip = false,
        )
        assertTrue(items.any { it.kind == HelixPaletteKind.TelosApp && it.title == "Files" })
        assertTrue(items.any { it.kind == HelixPaletteKind.Desktop && it.title == "Firefox" })
        assertTrue(items.any { it.kind == HelixPaletteKind.Window && it.title == "Files" })
        assertFalse(items.any { it.title == "Notes" })
        val filesWin = items.first { it.kind == HelixPaletteKind.Window && it.title == "Files" }
        val firefoxHit = items.first { it.kind == HelixPaletteKind.Desktop }
        assertTrue(filesWin.score > firefoxHit.score)
    }

    @Test
    fun calculatorOutranksPartialAppMatch() {
        val items = HelixPaletteActions.build(
            query = "2+2",
            telos = telos,
            apps = emptyList(),
        )
        assertEquals(HelixPaletteKind.Calculator, items.first().kind)
        assertEquals("4", items.first().title)
    }

    @Test
    fun killAndPasteHiddenWhenUnavailable() {
        val items = HelixPaletteActions.build(
            query = "kill",
            hasFocusedWindow = false,
            hasVaultClip = false,
            telos = emptyList(),
        )
        assertFalse(items.any { it.commandId == HelixPaletteActions.CMD_KILL })
        assertFalse(items.any { it.commandId == HelixPaletteActions.CMD_PASTE_VAULT })

        val withKill = HelixPaletteActions.build(
            query = "close",
            hasFocusedWindow = true,
            hasVaultClip = false,
            telos = emptyList(),
        )
        assertTrue(withKill.any { it.commandId == HelixPaletteActions.CMD_KILL })

        val withPaste = HelixPaletteActions.build(
            query = "vault",
            hasFocusedWindow = false,
            hasVaultClip = true,
            telos = emptyList(),
        )
        assertTrue(withPaste.any { it.commandId == HelixPaletteActions.CMD_PASTE_VAULT })
    }

    @Test
    fun privacySafeRootsSkipHostHomeOutsidePortableOrVault() {
        val tmp = createTempDirectory(prefix = "helix-palette-").toFile()
        try {
            val portable = File(tmp, "portable").apply { mkdirs() }
            File(portable, "Documents").mkdirs()
            val host = File(tmp, "host-home").apply { mkdirs() }
            File(host, "Documents").mkdirs()
            val vault = File(tmp, "vault").apply { mkdirs() }
            File(vault, "Documents").mkdirs()

            val portableRoots = HelixPaletteActions.privacySafeFileRoots(
                xdg = { listOf(File(host, "Documents"), host) },
                portable = true,
                portableRoot = { portable },
                sessionHome = { host },
                hostHome = { host },
                vaultMount = { null },
            )
            assertTrue(portableRoots.all { it.absolutePath.startsWith(portable.absolutePath) })
            assertFalse(portableRoots.any { it.absolutePath.startsWith(host.absolutePath) })

            val vaultRoots = HelixPaletteActions.privacySafeFileRoots(
                xdg = { listOf(File(vault, "Documents"), File(host, "Documents"), vault) },
                portable = false,
                portableRoot = { portable },
                sessionHome = { vault },
                hostHome = { host },
                vaultMount = { vault },
            )
            assertTrue(vaultRoots.any { it.absolutePath == vault.absolutePath || it.absolutePath.startsWith(vault.absolutePath + File.separator) })
            assertFalse(vaultRoots.any { it.canonicalFile.absolutePath.startsWith(host.canonicalFile.absolutePath + File.separator) || it.canonicalFile == host.canonicalFile })

            val filtered = HelixPaletteActions.filterRootsUnderHome(
                listOf(File(vault, "Documents"), File(host, "Documents")),
                vault,
            )
            assertEquals(1, filtered.size)
            assertTrue(filtered.single().absolutePath.contains("vault"))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun rankHelpersMatchDesktopSearch() {
        assertTrue(HelixPaletteActions.rank("set", "Settings") > HelixPaletteActions.rank("set", "Network"))
        assertEquals(0, HelixPaletteActions.rank("zzz", "Settings"))
    }
}
