package com.telos.desktop.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsPackageStoreTest {
    @Test
    fun detectBackendsFromWhichMock() {
        assertEquals(
            listOf(WindowsPackageBackend.Winget, WindowsPackageBackend.Scoop),
            WindowsPackageStore.detectBackendsFromWhich(
                mapOf(
                    "winget" to "C:\\Windows\\System32\\winget.exe",
                    "scoop" to "C:\\Users\\a\\scoop\\shims\\scoop.cmd",
                ),
            ),
        )
        assertEquals(
            listOf(WindowsPackageBackend.Winget),
            WindowsPackageStore.detectBackendsFromWhich(mapOf("winget.exe" to "C:\\winget.exe")),
        )
        assertEquals(
            listOf(WindowsPackageBackend.Scoop),
            WindowsPackageStore.detectBackendsFromWhich(mapOf("scoop.cmd" to "C:\\scoop.cmd")),
        )
        assertTrue(WindowsPackageStore.detectBackendsFromWhich(emptyMap()).isEmpty())
        assertTrue(
            WindowsPackageStore.detectBackendsFromWhich(
                mapOf("winget" to null, "scoop" to ""),
            ).isEmpty(),
        )
    }

    @Test
    fun parsesWingetListTable() {
        val rows = WindowsPackageStore.parseWingetList(
            """
            Name               Id                   Version  Source
            -------------------------------------------------------
            Mozilla Firefox    Mozilla.Firefox      128.0    winget
            Notepad++          Notepad++.Notepad++  8.6.9    winget
            """.trimIndent(),
            installed = true,
        )
        assertEquals(2, rows.size)
        assertEquals("Mozilla.Firefox", rows[0].id)
        assertEquals("Mozilla Firefox", rows[0].name)
        assertEquals("128.0", rows[0].version)
        assertTrue(rows[0].installed)
        assertEquals(WindowsPackageBackend.Winget, rows[0].source)
    }

    @Test
    fun parsesWingetSearchTable() {
        val rows = WindowsPackageStore.parseWingetSearch(
            """
            Name       Id              Version Match        Source
            ------------------------------------------------------
            Git        Git.Git         2.45.1  Command: git winget
            GitHub CLI GitHub.cli      2.50.0               winget
            """.trimIndent(),
        )
        assertEquals(2, rows.size)
        assertEquals("Git.Git", rows[0].id)
        assertFalse(rows[0].installed)
    }

    @Test
    fun parsesScoopListAndSearch() {
        val installed = WindowsPackageStore.parseScoopList(
            """
            Installed apps:

            Name   Version Source Updated          Info
            ----   ------- ------ -------          ----
            7zip   24.07   main   2024-07-01 12:00
            git    2.45.1  main   2024-06-01 09:00
            """.trimIndent(),
            installed = true,
        )
        assertEquals(2, installed.size)
        assertEquals("7zip", installed[0].id)
        assertEquals(WindowsPackageBackend.Scoop, installed[0].source)

        val search = WindowsPackageStore.parseScoopSearch(
            """
            Name    Version Source Binaries
            ----    ------- ------ --------
            ffmpeg  7.0     main
            firefox 128.0   main
            """.trimIndent(),
        )
        assertEquals(2, search.size)
        assertEquals("ffmpeg", search[0].id)
        assertFalse(search[0].installed)
    }
}
