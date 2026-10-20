package de.mm20.launcher2.ui.component

import org.junit.Assert.*
import org.junit.Test

class FrostedGeometryTest {
    @Test fun portraitAndLandscapeShareTheWholeWindowCrop() {
        val portrait = FrostedGeometry.crop(512, 256, 400, 800)!!
        assertEquals(1600, portrait.width); assertEquals(800, portrait.height)
        assertEquals(-600f, portrait.left, .001f); assertEquals(0f, portrait.top, .001f)
        val landscape = FrostedGeometry.crop(256, 512, 800, 400)!!
        assertEquals(800, landscape.width); assertEquals(1600, landscape.height)
        assertEquals(0f, landscape.left, .001f); assertEquals(-600f, landscape.top, .001f)
    }
    @Test fun splitWindowsAndMissingLayoutsCannotCreateInvalidCrops() {
        assertNull(FrostedGeometry.crop(512, 512, 0, 800))
        val crop = FrostedGeometry.crop(512, 512, 300, 600)!!
        assertEquals(600, crop.width); assertEquals(-150f, crop.left, .001f)
    }
}
