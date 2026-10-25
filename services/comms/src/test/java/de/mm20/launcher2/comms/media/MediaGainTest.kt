package de.mm20.launcher2.comms.media
import org.junit.Assert.assertEquals
import org.junit.Test
class MediaGainTest {
    @Test fun twoTimesVolumeIsSixDecibels() { assertEquals(602, volumeGainMilliBel(2f)) }
    @Test fun normalVolumeHasNoBoostAndRangeIsBounded() {
        assertEquals(0, volumeGainMilliBel(1f)); assertEquals(0, volumeGainMilliBel(0f))
        assertEquals(602, volumeGainMilliBel(20f)); assertEquals(352, volumeGainMilliBel(1.5f))
    }
}
