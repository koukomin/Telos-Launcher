package de.mm20.launcher2.ui.tools

import org.junit.Assert.*
import org.junit.Test

class MagnifierFilterTest {
    private fun rgb(filter: MagnifierFilter, channel: Float): List<Float> {
        val matrix = filter.matrix!!
        return (0..2).map { row ->
            val i = row * 5
            (matrix[i] * channel + matrix[i + 1] * channel + matrix[i + 2] * channel + matrix[i + 4]).coerceIn(0f, 255f)
        }
    }
    @Test fun accessibilityFiltersPreserveLuminanceContrast() {
        assertEquals(listOf(0f, 0f, 0f), rgb(MagnifierFilter.YellowOnBlack, 0f))
        val yellow = rgb(MagnifierFilter.YellowOnBlack, 255f)
        assertEquals(255f, yellow[0], 0.01f)
        assertEquals(255f, yellow[1], 0.01f)
        assertEquals(0f, yellow[2], 0.01f)
        assertEquals(listOf(0f, 0f, 255f), rgb(MagnifierFilter.BlueOnYellow, 0f))
        val bright = rgb(MagnifierFilter.BlueOnYellow, 255f)
        assertEquals(255f, bright[0], 0.01f)
        assertEquals(255f, bright[1], 0.01f)
        assertEquals(0f, bright[2], 0.01f)
    }
}
