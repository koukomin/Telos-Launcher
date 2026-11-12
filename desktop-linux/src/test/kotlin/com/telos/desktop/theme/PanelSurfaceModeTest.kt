package com.telos.desktop.theme

import com.telos.desktop.settings.TelosDesktopSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties
import kotlin.io.path.createTempDirectory

class PanelSurfaceModeTest {
    @Test
    fun parsesAliasesAndDefaultsToBlurred() {
        assertEquals(PanelSurfaceMode.Transparent, PanelSurfaceMode.parse("transparent"))
        assertEquals(PanelSurfaceMode.Transparent, PanelSurfaceMode.parse("CLEAR"))
        assertEquals(PanelSurfaceMode.Blurred, PanelSurfaceMode.parse("blurred"))
        assertEquals(PanelSurfaceMode.Blurred, PanelSurfaceMode.parse("frost"))
        assertEquals(PanelSurfaceMode.Solid, PanelSurfaceMode.parse("solid"))
        assertEquals(PanelSurfaceMode.Solid, PanelSurfaceMode.parse("opaque"))
        assertEquals(PanelSurfaceMode.Blurred, PanelSurfaceMode.parse(null))
        assertEquals(PanelSurfaceMode.Blurred, PanelSurfaceMode.parse(""))
        assertEquals(PanelSurfaceMode.PREF_KEY, "panel.surfaceMode")
    }

    @Test
    fun transparentForcesZeroBlurAndKeepsConfigurableAlpha() {
        val r = PanelSurfaceMapping.resolve(
            PanelSurfaceMode.Transparent,
            panelAlpha = 0.42f,
            panelBlur = 24f,
        )
        assertEquals(PanelSurfaceMode.Transparent, r.mode)
        assertEquals(0.42f, r.alpha, 0.001f)
        assertEquals(0f, r.blurRadius, 0.001f)
        assertFalse(r.solidSurfaceContainer)
        assertFalse(r.frostPlateFallback)
    }

    @Test
    fun blurredUsesPanelBlurAndMarksFrostFallbackWhenHostBlurMissing() {
        val r = PanelSurfaceMapping.resolve(
            PanelSurfaceMode.Blurred,
            panelAlpha = 0.8f,
            panelBlur = 16f,
            hostBackdropBlurAvailable = false,
        )
        assertEquals(0.8f, r.alpha, 0.001f)
        assertEquals(16f, r.blurRadius, 0.001f)
        assertFalse(r.solidSurfaceContainer)
        assertTrue(r.frostPlateFallback)

        val withHost = PanelSurfaceMapping.resolve(
            PanelSurfaceMode.Blurred,
            panelAlpha = 0.8f,
            panelBlur = 16f,
            hostBackdropBlurAvailable = true,
        )
        assertFalse(withHost.frostPlateFallback)
    }

    @Test
    fun solidIsOpaqueSurfaceContainerWithNoBlur() {
        val r = PanelSurfaceMapping.resolve(
            PanelSurfaceMode.Solid,
            panelAlpha = 0.3f,
            panelBlur = 20f,
        )
        assertEquals(1f, r.alpha, 0.001f)
        assertEquals(0f, r.blurRadius, 0.001f)
        assertTrue(r.solidSurfaceContainer)
        assertFalse(r.frostPlateFallback)
    }

    @Test
    fun clampsAlphaAndBlurRanges() {
        val low = PanelSurfaceMapping.resolve(
            PanelSurfaceMode.Transparent,
            panelAlpha = -1f,
            panelBlur = 99f,
        )
        assertEquals(PanelSurfaceMapping.MIN_GLASS_ALPHA, low.alpha, 0.001f)
        assertEquals(0f, low.blurRadius, 0.001f)

        val hi = PanelSurfaceMapping.resolve(
            PanelSurfaceMode.Blurred,
            panelAlpha = 2f,
            panelBlur = 99f,
        )
        assertEquals(1f, hi.alpha, 0.001f)
        assertEquals(PanelSurfaceMapping.MAX_BLUR, hi.blurRadius, 0.001f)
    }

    @Test
    fun persistsSurfaceModeAndPreservesPanelAlphaBlur() {
        val dir = createTempDirectory("telos-panel-surface").toFile()
        try {
            File(dir, "desktop.properties").writeText(
                "panelAlpha=0.55\npanelBlur=8.0\ntheme.workspaceTints=true\n",
            )
            val settings = TelosDesktopSettings(dir).also { it.load() }
            assertEquals(PanelSurfaceMode.Blurred.propertyValue, settings.panelSurfaceMode)
            assertEquals(0.55f, settings.panelAlpha, 0.001f)
            assertEquals(8f, settings.panelBlur, 0.001f)

            settings.panelSurfaceMode = PanelSurfaceMode.Transparent.propertyValue
            settings.panelAlpha = 0.35f
            settings.save()

            val text = File(dir, "desktop.properties").readText()
            assertTrue(text.contains("panel.surfaceMode=transparent"))
            assertTrue(text.contains("panelAlpha=0.35"))
            assertTrue(text.contains("theme.workspaceTints=true"))

            val loaded = TelosDesktopSettings(dir).also { it.load() }
            assertEquals(PanelSurfaceMode.Transparent.propertyValue, loaded.panelSurfaceMode)
            val resolved = PanelSurfaceMapping.resolve(
                PanelSurfaceMode.parse(loaded.panelSurfaceMode),
                loaded.panelAlpha,
                loaded.panelBlur,
            )
            assertEquals(0.35f, resolved.alpha, 0.001f)
            assertEquals(0f, resolved.blurRadius, 0.001f)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readsSurfaceModeFromProperties() {
        val p = Properties()
        assertEquals(PanelSurfaceMode.Blurred, PanelSurfaceMode.parse(p))
        p[PanelSurfaceMode.PREF_KEY] = "solid"
        assertEquals(PanelSurfaceMode.Solid, PanelSurfaceMode.parse(p))
    }
}
