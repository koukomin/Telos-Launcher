package de.mm20.launcher2.ui.component

import de.mm20.launcher2.uikmp.TelosSurfaceTokens as SharedSurfaceTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compile/unit parity: Android role names stay aligned with the shared ui-kmp kit.
 */
class TelosSurfaceTokensAndroidTest {
    @Test
    fun androidRolesMatchSharedKit() {
        assertEquals(SharedSurfaceTokens.roles, TelosSurfaceTokens.roles)
        assertEquals(SharedSurfaceTokens.ROLE_PANEL_GLASS, TelosSurfaceTokens.ROLE_PANEL_GLASS)
        assertEquals(SharedSurfaceTokens.ROLE_CARD_OPAQUE, TelosSurfaceTokens.ROLE_CARD_OPAQUE)
        assertEquals(SharedSurfaceTokens.ROLE_POPUP_OPAQUE, TelosSurfaceTokens.ROLE_POPUP_OPAQUE)
        assertEquals(SharedSurfaceTokens.ROLE_HUD_LABEL, TelosSurfaceTokens.ROLE_HUD_LABEL)
        assertEquals(SharedSurfaceTokens.ROLE_TECH_PILL, TelosSurfaceTokens.ROLE_TECH_PILL)
    }

    @Test
    fun sharedPopupContractStaysOpaque() {
        assertTrue(SharedSurfaceTokens.isPopupContractIntact(SharedSurfaceTokens.popupOpaque))
        assertEquals(1f, SharedSurfaceTokens.popupOpaque.alpha, 0.001f)
    }
}
