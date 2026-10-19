package de.mm20.launcher2.ui.system

import org.junit.Assert.*
import org.junit.Test

class CpuTelemetryTest {
    @Test fun unavailableOrInvalidCountersStayUnknown() {
        assertNull(parseCpuStat(null))
        assertNull(parseCpuStat("cpu0 1 2 3 4"))
        assertNull(parseCpuStat("cpu 1 bad 3 4"))
        assertNull(cpuUsage(null, 1L to 10L))
        assertNull(cpuUsage(1L to 10L, 1L to 10L))
        assertNull(cpuUsage(10L to 100L, 9L to 110L))
    }

    @Test fun iowaitIsIdleAndGuestTimeIsNotCountedTwice() {
        assertEquals(45L to 71L, parseCpuStat("cpu 10 2 3 40 5 1 2 8 6 7"))
        assertEquals(0.75f, cpuUsage(20L to 100L, 25L to 120L)!!, 0.0001f)
    }
}
