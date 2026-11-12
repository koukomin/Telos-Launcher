package com.telos.desktop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelixSettingsIndexTest {
    private val entries = listOf(
        HelixSettingsIndexEntry(
            id = "session",
            title = "Session",
            keywords = listOf("fullscreen", "theme", "idle"),
        ),
        HelixSettingsIndexEntry(
            id = "night_light",
            title = "Night Light",
            keywords = listOf("Enable Night Light", "temperature", "gammastep", "redshift"),
        ),
        HelixSettingsIndexEntry(
            id = "opsec",
            title = "OpSec",
            keywords = listOf("Secure clipboard", "audit"),
        ),
        HelixSettingsIndexEntry(
            id = "cursor",
            title = "Cursor",
            keywords = listOf("XCursor", "size"),
        ),
    )

    @Test
    fun blankQueryKeepsAll() {
        assertEquals(entries.map { it.id }.toSet(), HelixSettingsIndex.filterIds(entries, ""))
        assertEquals(entries.map { it.id }.toSet(), HelixSettingsIndex.filterIds(entries, "   "))
    }

    @Test
    fun filtersByTitle() {
        val ids = HelixSettingsIndex.filterIds(entries, "night")
        assertEquals(setOf("night_light"), ids)
    }

    @Test
    fun filtersByToggleKeyword() {
        val ids = HelixSettingsIndex.filterIds(entries, "clipboard")
        assertEquals(setOf("opsec"), ids)
    }

    @Test
    fun filtersCaseInsensitive() {
        assertEquals(setOf("cursor"), HelixSettingsIndex.filterIds(entries, "XCURSOR"))
        assertTrue(HelixSettingsIndex.matches(entries[1], "GAMMASTEP"))
    }

    @Test
    fun unknownQueryYieldsEmpty() {
        assertTrue(HelixSettingsIndex.filter(entries, "zzzz-no-match").isEmpty())
        assertFalse(HelixSettingsIndex.matches(entries[0], "night"))
    }
}
