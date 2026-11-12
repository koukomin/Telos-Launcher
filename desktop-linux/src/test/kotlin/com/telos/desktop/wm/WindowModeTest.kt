package com.telos.desktop.wm

import com.telos.desktop.settings.TelosDesktopSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class WindowModeTest {
    @Test
    fun parseAliases() {
        assertEquals(WindowMode.Tiling, WindowMode.parse("tiling"))
        assertEquals(WindowMode.Tiling, WindowMode.parse("niri"))
        assertEquals(WindowMode.Stacking, WindowMode.parse("stacking"))
        assertEquals(WindowMode.Stacking, WindowMode.parse("labwc"))
        assertEquals(WindowMode.Stacking, WindowMode.parse(null))
        assertEquals(WindowMode.Stacking, WindowMode.parse("unknown"))
    }

    @Test
    fun persistsInDesktopProperties() {
        val dir = createTempDirectory("telos-window-mode").toFile()
        try {
            val settings = TelosDesktopSettings(dir)
            settings.windowMode = WindowMode.Tiling.propertyValue
            settings.save()
            val text = File(dir, "desktop.properties").readText()
            assertTrue(text.contains("desktop.windowMode=tiling"))
            val loaded = TelosDesktopSettings(dir).also { it.load() }
            assertEquals(WindowMode.Tiling.propertyValue, loaded.windowMode)
            loaded.windowMode = WindowMode.Stacking.propertyValue
            loaded.save()
            val again = TelosDesktopSettings(dir).also { it.load() }
            assertEquals(WindowMode.Stacking.propertyValue, again.windowMode)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
