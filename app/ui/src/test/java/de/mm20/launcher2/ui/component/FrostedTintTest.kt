package de.mm20.launcher2.ui.component

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class FrostedTintTest {
    @Test fun unavailableRootBackdropUsesOpaqueThemeTone() {
        for (tone in listOf(Color(0xFFF4EFFA), Color(0xFF18141C))) {
            for (alpha in listOf(0f, 0.4f, 1f)) {
                assertEquals(tone, frostedTint(tone.copy(alpha = alpha), false, tone.copy(alpha = 0f)))
            }
        }
    }

    @Test fun availableBackdropPreservesRequestedTransparency() {
        val requested = Color(0xFFF4EFFA).copy(alpha = 0.35f)
        assertEquals(requested, frostedTint(requested, true, Color.Black))
    }

    @Test fun innerChromeWithoutFallbackStaysTransparent() {
        assertEquals(Color.Transparent, frostedTint(Color.Transparent, false, null))
    }
}
