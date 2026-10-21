package de.mm20.launcher2.ui.layout

import org.junit.Assert.*
import org.junit.Test

class AdaptiveLayoutPolicyTest {
    private val scales = listOf(0.85f, 1f, 1.3f, 2f)

    @Test fun gridFitsAvailableWidthAcrossWindowFontAndDisplayScales() {
        for (physicalWidth in listOf(480f, 960f, 1440f, 2400f)) {
            for (density in listOf(1f, 1.5f, 2f)) for (scale in scales) {
                val width = physicalWidth / density
                val minimum = maxOf(64f, 72f * scale)
                for (preferred in 1..12) {
                    val count = AdaptiveLayoutPolicy.columns(width, minimum, preferred)
                    assertTrue(count in 1..preferred)
                    assertTrue("$width dp / $scale: cells must fit", count * minimum <= width || count == 1)
                    if (count < preferred) assertTrue((count + 1) * minimum > width)
                }
            }
        }
    }

    @Test fun gridHonorsUserMaximumAndHandlesTransientZeroSize() {
        assertEquals(3, AdaptiveLayoutPolicy.columns(1600f, 72f, 3))
        assertEquals(1, AdaptiveLayoutPolicy.columns(0f, 72f, 0))
        assertEquals(4, AdaptiveLayoutPolicy.columns(288f, 72f, 5))
        assertEquals(3, AdaptiveLayoutPolicy.columns(287f, 72f, 5))
    }

    @Test fun displayScalingChangesLayoutWithoutOrientationChange() {
        assertTrue(AdaptiveLayoutPolicy.sideBySide(1200f, 800f, 1f))
        assertFalse(AdaptiveLayoutPolicy.sideBySide(1200f / 2, 800f / 2, 1f))
    }

    @Test fun panesRequireBothWidthAndHeightAndRespectLargeText() {
        assertFalse(AdaptiveLayoutPolicy.sideBySide(640f, 1000f, 1f))
        assertFalse(AdaptiveLayoutPolicy.sideBySide(1200f, 240f, 1f))
        assertTrue(AdaptiveLayoutPolicy.sideBySide(840f, 480f, 1f))
        assertFalse(AdaptiveLayoutPolicy.sideBySide(840f, 480f, 1.3f))
        assertFalse(AdaptiveLayoutPolicy.sideBySide(1200f, 800f, 2f))
        assertTrue(AdaptiveLayoutPolicy.sideBySide(1440f, 900f, 2f))
    }

    @Test fun settingsMoveControlsBeforeTextIsStarved() {
        assertTrue(AdaptiveLayoutPolicy.stackPreference(320f, 1f))
        assertFalse(AdaptiveLayoutPolicy.stackPreference(600f, 1f))
        assertTrue(AdaptiveLayoutPolicy.stackPreference(600f, 2f))
        assertFalse(AdaptiveLayoutPolicy.stackPreference(840f, 2f))
    }

    @Test fun readingColumnHasBoundedWidthAndNonnegativeGutters() {
        for (width in listOf(240f, 320f, 600f, 840f, 1200f, 2000f)) {
            val gutter = AdaptiveLayoutPolicy.contentGutter(width)
            assertTrue(gutter >= 12f)
            assertTrue(width - gutter * 2 <= 840f)
        }
    }

    @Test fun scientificKeysScrollRatherThanShrinkAtLargeFont() {
        for (width in listOf(240f, 320f, 600f, 840f)) for (scale in scales) for (columns in 4..5) {
            val content = AdaptiveLayoutPolicy.keypadWidth(width, columns, scale)
            assertTrue(content >= width)
            val usableKeyWidth = (content - 24f) / columns - 8f
            assertTrue(usableKeyWidth + 0.01f >= 72f * scale)
            assertTrue(usableKeyWidth >= 48f)
        }
    }

    @Test fun scaleAboveTwoIsNotSilentlyCapped() {
        assertTrue(AdaptiveLayoutPolicy.keypadWidth(320f, 5, 3f) > AdaptiveLayoutPolicy.keypadWidth(320f, 5, 2f))
        assertTrue(AdaptiveLayoutPolicy.columns(840f, 72f * 3f) < AdaptiveLayoutPolicy.columns(840f, 72f * 2f))
    }
}
