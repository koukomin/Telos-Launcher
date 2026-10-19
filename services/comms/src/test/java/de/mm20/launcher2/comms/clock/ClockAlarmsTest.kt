package de.mm20.launcher2.comms.clock

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockAlarmsTest {
    @Test fun elapsedTimeSchedulesTomorrow() {
        val now = ZonedDateTime.of(2026, 10, 19, 9, 30, 0, 0, ZoneId.of("Europe/Athens"))
        val expected = now.plusDays(1).withHour(7).withMinute(0)
        assertEquals(expected.toInstant().toEpochMilli(), ClockAlarms.nextOccurrence(now, 7, 0))
    }

    @Test fun tomorrowUsesLocalTimeAcrossDaylightSavingBoundary() {
        val now = ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, ZoneId.of("Europe/Athens"))
        val expected = ZonedDateTime.of(2026, 10, 25, 7, 0, 0, 0, now.zone)
        assertEquals(expected.toInstant().toEpochMilli(), ClockAlarms.nextOccurrence(now, 7, 0))
    }

    @Test fun sameMinuteWithElapsedSecondsSchedulesTomorrow() {
        val now = ZonedDateTime.of(2026, 10, 19, 7, 0, 1, 0, ZoneId.of("UTC"))
        val expected = now.plusDays(1).withSecond(0)
        assertEquals(expected.toInstant().toEpochMilli(), ClockAlarms.nextOccurrence(now, 7, 0))
    }
}
