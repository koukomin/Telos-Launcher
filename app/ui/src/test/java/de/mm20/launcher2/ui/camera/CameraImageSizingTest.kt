package de.mm20.launcher2.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraImageSizingTest {
    @Test fun oversizedOemOutputCannotDecodeAtFullResolution() {
        assertEquals(8, CameraImageSizing.sampleSize(16000, 12000, 5_000_000))
        assertEquals(2, CameraImageSizing.sampleSize(2560, 1920, 4_000_000))
    }
    @Test fun alreadyBoundedImagesRemainFullResolution() {
        assertEquals(1, CameraImageSizing.sampleSize(2560, 1920, 5_000_000))
    }
    @Test(expected = IllegalArgumentException::class)
    fun corruptHeaderIsRejectedBeforeAllocation() {
        CameraImageSizing.sampleSize(-1, -1, 5_000_000)
    }
}
