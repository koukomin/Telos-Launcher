package de.mm20.launcher2.ui.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Covers [computeTabY], the geometry behind where each zone's small overlay window ends up on
 * screen. This can't exercise the actual bug that prompted the floating launcher's windowing
 * rewrite (real touch dispatch needs WindowManager, i.e. a real device - this project has no
 * instrumented test setup) - what it does verify is that a tab is always positioned fully within
 * the screen, never (say) with a negative y or one that pushes it below the bottom edge, which
 * would be a regression in its own right independent of the touch-lockout fix.
 */
class FloatingLauncherTabPositionTest {

    private val screenHeightPx = 2400
    private val tabHeightPx = 200

    @Test
    fun `top zone sits near the top of the screen`() {
        val y = computeTabY(screenHeightPx, tabHeightPx, verticalFraction = 1f / 6f)
        assertEquals(((screenHeightPx - tabHeightPx) / 6f).roundToInt(), y)
    }

    @Test
    fun `middle zone is vertically centered`() {
        val y = computeTabY(screenHeightPx, tabHeightPx, verticalFraction = 0.5f)
        assertEquals((screenHeightPx - tabHeightPx) / 2, y)
    }

    @Test
    fun `bottom zone sits near the bottom of the screen`() {
        val y = computeTabY(screenHeightPx, tabHeightPx, verticalFraction = 5f / 6f)
        assertEquals(((screenHeightPx - tabHeightPx) * 5 / 6f).roundToInt(), y)
    }

    @Test
    fun `a tab is never positioned off the top of the screen`() {
        for (fraction in listOf(0f, 1f / 6f, 0.5f, 5f / 6f, 1f)) {
            val y = computeTabY(screenHeightPx, tabHeightPx, fraction)
            assertTrue("y=$y should be >= 0 for fraction=$fraction", y >= 0)
        }
    }

    @Test
    fun `a tab never extends past the bottom of the screen`() {
        for (fraction in listOf(0f, 1f / 6f, 0.5f, 5f / 6f, 1f)) {
            val y = computeTabY(screenHeightPx, tabHeightPx, fraction)
            assertTrue(
                "y=$y + tabHeightPx=$tabHeightPx should be <= screenHeightPx=$screenHeightPx for fraction=$fraction",
                y + tabHeightPx <= screenHeightPx,
            )
        }
    }
}
