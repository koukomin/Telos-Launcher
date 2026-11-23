package de.mm20.launcher2.ui.keyboard

import org.junit.Assert.assertTrue
import org.junit.Test

class TelosKeyboardAppearanceTest {
    @Test
    fun scaledSizesStayInRange() {
        TelosKeyboardAppearance.keyboardScale = 2f
        TelosKeyboardAppearance.keyHeightDp = 72f
        TelosKeyboardAppearance.keyFontSp = 26f
        assertTrue(TelosKeyboardAppearance.scaledKeyHeightDp() <= 80f)
        assertTrue(TelosKeyboardAppearance.scaledFontSp() <= 28f)
        TelosKeyboardAppearance.keyboardScale = 1f
        TelosKeyboardAppearance.keyHeightDp = 52f
        TelosKeyboardAppearance.keyFontSp = 18f
    }
}
