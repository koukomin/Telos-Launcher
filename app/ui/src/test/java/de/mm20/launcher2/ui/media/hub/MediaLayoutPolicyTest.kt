package de.mm20.launcher2.ui.media.hub

import org.junit.Assert.*
import org.junit.Test

class MediaLayoutPolicyTest {
    @Test fun shortLandscapeKeepsBottomNavigationAndContentSpace() {
        assertFalse(useMediaRail(1200f, 400f, 1f))
        assertTrue(useMediaRail(1000f, 700f, 1f))
    }
    @Test fun largeFontsDoNotForceARailIntoATabletWindow() {
        assertFalse(useMediaRail(1000f, 700f, 2f))
        assertTrue(useMediaRail(1800f, 1000f, 2f))
    }
}
