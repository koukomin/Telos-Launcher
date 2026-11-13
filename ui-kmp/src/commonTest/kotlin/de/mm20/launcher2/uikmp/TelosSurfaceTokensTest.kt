package de.mm20.launcher2.uikmp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelosSurfaceTokensTest {
    @Test
    fun roleListMatchesTierBSurfaceKit() {
        assertEquals(
            listOf("panelGlass", "cardOpaque", "popupOpaque", "hudLabel", "techPill"),
            TelosSurfaceTokens.roles,
        )
    }

    @Test
    fun onlyPanelGlassAllowsRootAlpha() {
        assertTrue(TelosSurfaceTokens.allowsRootAlpha(TelosSurfaceTokens.ROLE_PANEL_GLASS))
        assertFalse(TelosSurfaceTokens.allowsRootAlpha(TelosSurfaceTokens.ROLE_CARD_OPAQUE))
        assertFalse(TelosSurfaceTokens.allowsRootAlpha(TelosSurfaceTokens.ROLE_POPUP_OPAQUE))
        assertFalse(TelosSurfaceTokens.allowsRootAlpha(TelosSurfaceTokens.ROLE_TECH_PILL))
        assertFalse(TelosSurfaceTokens.allowsRootAlpha(TelosSurfaceTokens.ROLE_HUD_LABEL))
    }

    @Test
    fun opaqueRolesStayFullyOpaque() {
        assertEquals(1f, TelosSurfaceTokens.cardOpaque.alpha)
        assertEquals(1f, TelosSurfaceTokens.popupOpaque.alpha)
        assertEquals(1f, TelosSurfaceTokens.techPill.alpha)
        assertTrue(TelosSurfaceTokens.isPopupContractIntact(TelosSurfaceTokens.popupOpaque))
        assertTrue(TelosSurfaceTokens.requiresOpaque(TelosSurfaceTokens.ROLE_POPUP_OPAQUE))
        assertTrue(TelosSurfaceTokens.requiresOpaque(TelosSurfaceTokens.ROLE_CARD_OPAQUE))
        assertTrue(TelosSurfaceTokens.requiresOpaque(TelosSurfaceTokens.ROLE_TECH_PILL))
    }

    @Test
    fun panelGlassAppliesRootAlphaOnly() {
        // Compose Color stores alpha in 8-bit channels, so exact floats quantize.
        val glass = TelosSurfaceTokens.panelGlass(alpha = 0.88f)
        assertEquals(0.88f, glass.alpha, absoluteTolerance = 0.01f)
        val defaultGlass = TelosSurfaceTokens.panelGlass()
        assertEquals(
            TelosSurfaceTokens.DEFAULT_PANEL_GLASS_ALPHA,
            defaultGlass.alpha,
            absoluteTolerance = 0.01f,
        )
        assertTrue(glass.alpha < 1f)
    }

    @Test
    fun hudLabelUsesSharedSecondaryAccent() {
        assertEquals(TelosSharedColors.secondary, TelosSurfaceTokens.hudLabel)
    }
}
