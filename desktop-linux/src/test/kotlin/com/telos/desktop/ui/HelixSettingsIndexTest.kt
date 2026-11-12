package com.telos.desktop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelixSettingsIndexTest {
    private val entries = listOf(
        HelixSettingsIndexEntry(
            id = "theme_studio",
            title = "Theme Studio",
            keywords = listOf("Quiet Ops", "Vault Neon", "density", "accent", "appearance"),
        ),
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
        assertEquals(setOf("theme_studio"), HelixSettingsIndex.filterIds(entries, "Theme Studio"))
    }

    @Test
    fun filtersByToggleKeyword() {
        val ids = HelixSettingsIndex.filterIds(entries, "clipboard")
        assertEquals(setOf("opsec"), ids)
    }

    @Test
    fun filtersCaseInsensitive() {
        assertEquals(setOf("cursor"), HelixSettingsIndex.filterIds(entries, "XCURSOR"))
        assertTrue(HelixSettingsIndex.matches(entries.first { it.id == "night_light" }, "GAMMASTEP"))
        assertTrue(HelixSettingsIndex.matches(entries.first { it.id == "theme_studio" }, "vault neon"))
    }

    @Test
    fun unknownQueryYieldsEmpty() {
        assertTrue(HelixSettingsIndex.filter(entries, "zzzz-no-match").isEmpty())
        assertFalse(HelixSettingsIndex.matches(entries.first { it.id == "session" }, "night"))
    }
}
