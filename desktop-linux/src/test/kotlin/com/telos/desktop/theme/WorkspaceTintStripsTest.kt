package com.telos.desktop.theme

import com.telos.desktop.settings.TelosDesktopSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties
import kotlin.io.path.createTempDirectory

class WorkspaceTintStripsTest {
    @Test
    fun mapsWorkspacesOneThroughFourToNeonPalette() {
        assertEquals(0x00E676L, WorkspaceTintStrips.rgbForWorkspace(1))
        assertEquals(0x00F0FFL, WorkspaceTintStrips.rgbForWorkspace(2))
        assertEquals(0xFF007FL, WorkspaceTintStrips.rgbForWorkspace(3))
        assertEquals(0xFF9100L, WorkspaceTintStrips.rgbForWorkspace(4))
        assertNull(WorkspaceTintStrips.rgbForWorkspace(0))
        assertNull(WorkspaceTintStrips.rgbForWorkspace(5))
        assertEquals(4, WorkspaceTintStrips.WORKSPACE_RGB.size)
    }

    @Test
    fun packsLowAlphaArgbWithoutChangingRgb() {
        val argb = WorkspaceTintStrips.argbForWorkspace(2, 0.25f)!!
        assertEquals(0x00F0FFL, argb and 0xFFFFFFL)
        val alpha = ((argb ushr 24) and 0xFFL).toInt()
        assertEquals(63, alpha) // 0.25 * 255 truncates toward 63
    }

    @Test
    fun quietOpsUsesLowerAlphaThanDefault() {
        assertTrue(
            WorkspaceTintStrips.stripAlpha(quietOps = true, active = false) <
                WorkspaceTintStrips.stripAlpha(quietOps = false, active = false),
        )
        assertTrue(
            WorkspaceTintStrips.stripAlpha(quietOps = false, active = true) >
                WorkspaceTintStrips.stripAlpha(quietOps = false, active = false),
        )
    }

    @Test
    fun panicMuteAndPreferenceGateVisibility() {
        assertTrue(WorkspaceTintStrips.shouldShow(workspaceTintsEnabled = true, panicMute = false))
        assertFalse(WorkspaceTintStrips.shouldShow(workspaceTintsEnabled = false, panicMute = false))
        assertFalse(WorkspaceTintStrips.shouldShow(workspaceTintsEnabled = true, panicMute = true))
    }

    @Test
    fun readsQuietOpsAndPanicMuteKeysDefensively() {
        val p = Properties()
        assertFalse(WorkspaceTintStrips.parseQuietOps(p))
        assertFalse(WorkspaceTintStrips.parsePanicMute(p))
        assertTrue(WorkspaceTintStrips.parseWorkspaceTints(p, default = true))

        p["theme.intensity"] = "quiet"
        assertTrue(WorkspaceTintStrips.parseQuietOps(p))
        p.remove("theme.intensity")
        p["theme.quietOps"] = "true"
        assertTrue(WorkspaceTintStrips.parseQuietOps(p))

        p["theme.panicMute"] = "true"
        assertTrue(WorkspaceTintStrips.parsePanicMute(p))
        p.remove("theme.panicMute")
        p["theme.muteNeon"] = "true"
        assertTrue(WorkspaceTintStrips.parsePanicMute(p))

        p[WorkspaceTintStrips.PREF_KEY] = "false"
        assertFalse(WorkspaceTintStrips.parseWorkspaceTints(p, default = true))
    }

    @Test
    fun persistsWorkspaceTintsPreferenceAndPreservesPanicMuteKey() {
        val dir = createTempDirectory("telos-workspace-tints").toFile()
        try {
            File(dir, "desktop.properties").writeText(
                "theme.panicMute=true\ntheme.intensity=quiet\n",
            )
            val settings = TelosDesktopSettings(dir).also { it.load() }
            assertTrue(settings.workspaceTints)
            assertTrue(settings.themePanicMute)
            assertTrue(settings.themeQuietOps)
            assertFalse(WorkspaceTintStrips.shouldShow(settings.workspaceTints, settings.themePanicMute))

            settings.workspaceTints = false
            settings.save()
            val text = File(dir, "desktop.properties").readText()
            assertTrue(text.contains("theme.workspaceTints=false"))
            assertTrue(text.contains("theme.panicMute=true"))
            assertTrue(text.contains("theme.intensity=quiet"))

            val loaded = TelosDesktopSettings(dir).also { it.load() }
            assertFalse(loaded.workspaceTints)
            assertTrue(loaded.themePanicMute)
            assertTrue(loaded.themeQuietOps)
        } finally {
            dir.deleteRecursively()
        }
    }
}
