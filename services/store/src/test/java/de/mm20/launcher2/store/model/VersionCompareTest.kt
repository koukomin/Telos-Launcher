package de.mm20.launcher2.store.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun readsTheNumbersOfAVersionName() {
        assertEquals(listOf(1L, 4L, 2L), VersionCompare.numbers("v1.4.2"))
        assertEquals(listOf(2L, 0L), VersionCompare.numbers("release-2.0"))
        assertEquals(listOf(2024L, 3L, 1L), VersionCompare.numbers("2024.03.1"))
        assertEquals(listOf(7L), VersionCompare.numbers("build 7"))
        assertNull(VersionCompare.numbers("nightly"))
    }

    @Test
    fun newerVersionIsRecognised() {
        assertTrue(VersionCompare.isNewer("1.4.3", "1.4.2"))
        assertTrue(VersionCompare.isNewer("v1.10.0", "1.9.9"))
        assertTrue(VersionCompare.isNewer("2.0", "1.99.99"))
    }

    @Test
    fun sameOrOlderVersionIsNotAnUpdate() {
        assertFalse(VersionCompare.isNewer("1.4.2", "1.4.2"))
        assertFalse(VersionCompare.isNewer("v1.4.2", "1.4.2"))
        assertFalse(VersionCompare.isNewer("1.4", "1.4.0"))
        assertFalse(VersionCompare.isNewer("1.4.1", "1.4.2"))
    }

    @Test
    fun unknownVersionsNeverCountAsAnUpdate() {
        assertFalse(VersionCompare.isNewer("", "1.0"))
        assertFalse(VersionCompare.isNewer("unknown", "1.0"))
    }
}
