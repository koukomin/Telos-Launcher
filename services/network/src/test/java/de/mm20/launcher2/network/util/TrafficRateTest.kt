package de.mm20.launcher2.network.util

import org.junit.Assert.*
import org.junit.Test

class TrafficRateTest {
    @Test fun elapsedTimeRatherThanAssumedPollingInterval() {
        assertEquals(2000L, TrafficRate.bytesPerSecond(1000, 6000, 2500))
        assertEquals(0L, TrafficRate.bytesPerSecond(1000, 1000, 2000))
    }
    @Test fun unavailableResetAndInvalidTimeRemainUnknown() {
        assertNull(TrafficRate.bytesPerSecond(-1, 10, 2000))
        assertNull(TrafficRate.bytesPerSecond(100, 10, 2000))
        assertNull(TrafficRate.bytesPerSecond(0, 100, 0))
    }
}
