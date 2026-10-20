package de.mm20.launcher2.ui.theme

import org.junit.Assert.*
import org.junit.Test

class ContrastPolicyTest {
    @Test fun brightWallpaperUsesBlackWithoutScrim() {
        assertEquals(ContrastChoice(true, 0f), chooseContrast(0.8, 1.0))
    }
    @Test fun darkWallpaperUsesWhiteWithoutScrim() {
        assertEquals(ContrastChoice(false, 0f), chooseContrast(0.0, 0.08))
    }
    @Test fun mixedWallpaperCannotUseItsAverage() {
        assertTrue(chooseContrast(0.0, 1.0).scrimAlpha > 0f)
    }
    @Test fun missingPixelsUseStableWhiteOnBlackBacking() {
        assertEquals(ContrastChoice(false, 0.65f), chooseContrast())
    }
    @Test fun forcedColorsArePreservedAndProtected() {
        assertEquals(ContrastChoice(true, 0.8f), chooseContrast(0.0, 0.0, true))
        assertEquals(ContrastChoice(false, 0.65f), chooseContrast(1.0, 1.0, false))
    }
    @Test fun fallbackBackingsMeetNormalTextContrastAtWorstCase() {
        val whiteOverBlackScrimOnWhite = 1.05 / (relativeLuminance(0xff595959.toInt()) + 0.05)
        val blackOverWhiteScrimOnBlack = (relativeLuminance(0xffcccccc.toInt()) + 0.05) / 0.05
        assertTrue(whiteOverBlackScrimOnWhite >= 4.5)
        assertTrue(blackOverWhiteScrimOnBlack >= 4.5)
    }
    @Test fun srgbChannelsAreLinearized() {
        assertEquals(0.0, relativeLuminance(0xff000000.toInt()), 0.00001)
        assertEquals(1.0, relativeLuminance(0xffffffff.toInt()), 0.00001)
        assertEquals(0.2126, relativeLuminance(0xffff0000.toInt()), 0.00001)
    }
    @Test fun eightyPercentNeutralPanelsChooseMatchingForeground() {
        // Black at 80% over white is #333; white at 80% over black is #ccc.
        assertFalse(chooseContrast(0.0, relativeLuminance(0xff333333.toInt())).darkText)
        assertTrue(chooseContrast(relativeLuminance(0xffcccccc.toInt()), 1.0).darkText)
    }
    @Test fun localRegionsSelectTheirOwnForeground() {
        val samples = WallpaperSamples(2, 1, doubleArrayOf(0.8, 0.0), doubleArrayOf(1.0, 0.05), 2f)
        assertEquals(ContrastChoice(true, 0f), samples.choice(200f, 100f, rightFraction = 0.5f))
        assertEquals(ContrastChoice(false, 0f), samples.choice(200f, 100f, leftFraction = 0.5f))
        assertTrue(samples.choice(200f, 100f).scrimAlpha > 0f)
    }
    @Test fun changedViewportFallsBackInsteadOfReusingWrongCrop() {
        val samples = WallpaperSamples(1, 1, doubleArrayOf(1.0), doubleArrayOf(1.0), 2f)
        assertEquals(ContrastChoice(false, 0.65f), samples.choice(100f, 200f))
    }
}
