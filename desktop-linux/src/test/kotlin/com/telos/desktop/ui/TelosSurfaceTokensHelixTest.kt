package com.telos.desktop.ui

import de.mm20.launcher2.uikmp.TelosSurfaceTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Compile/unit guard: Helix chrome kit roles stay opaque where required. */
class TelosSurfaceTokensHelixTest {
    @Test
    fun helixChromeUsesSharedSurfaceKitRoles() {
        assertEquals(
            listOf("panelGlass", "cardOpaque", "popupOpaque", "hudLabel", "techPill"),
            TelosSurfaceTokens.roles,
        )
        assertTrue(TelosSurfaceTokens.isPopupContractIntact(TelosSurfaceTokens.popupOpaque))
        assertEquals(1f, TelosSurfaceTokens.cardOpaque.alpha, 0.001f)
        assertEquals(1f, TelosSurfaceTokens.techPill.alpha, 0.001f)
        assertFalse(TelosSurfaceTokens.allowsRootAlpha(TelosSurfaceTokens.ROLE_POPUP_OPAQUE))
        assertTrue(TelosSurfaceTokens.allowsRootAlpha(TelosSurfaceTokens.ROLE_PANEL_GLASS))
    }
}
