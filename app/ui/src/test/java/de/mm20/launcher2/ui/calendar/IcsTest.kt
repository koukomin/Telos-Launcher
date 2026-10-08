package de.mm20.launcher2.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsTest {
    @Test
    fun roundTrip() {
        val e = listOf(
            IcsEvent("Lunch, with; Anna", "line1\nline2", "Cafe", 1_700_000_000_000, 1_700_003_600_000, false, "FREQ=WEEKLY"),
            IcsEvent("Holiday", start = 1_700_000_000_000 / 86_400_000 * 86_400_000, end = 1_700_000_000_000 / 86_400_000 * 86_400_000 + 2 * 86_400_000, allDay = true),
        )
        val back = Ics.read(Ics.write("x", e))
        assertEquals(2, back.size)
        assertEquals("Lunch, with; Anna", back[0].title)
        assertEquals("line1\nline2", back[0].description)
        assertEquals(e[0].start, back[0].start); assertEquals(e[0].end, back[0].end)
        assertEquals("FREQ=WEEKLY", back[0].rrule)
        assertTrue(back[1].allDay); assertEquals(e[1].end, back[1].end)
    }

    @Test
    fun durationAndFolding() {
        assertEquals(3600, Ics.durationSeconds("P3600S")); assertEquals(5400, Ics.durationSeconds("PT1H30M")); assertEquals(86400, Ics.durationSeconds("P1D"))
        val long = "x".repeat(200)
        assertEquals(long, Ics.read(Ics.write("x", listOf(IcsEvent(long, start = 0, end = 1000))))[0].title)
    }

    @Test
    fun alarmDoesNotReplaceTheEventDescription() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:20240101T100000Z\r\nDTEND:20240101T110000Z\r\nSUMMARY:Meet\r\n" +
            "DESCRIPTION:Real\r\nBEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:Reminder\r\nTRIGGER:-PT10M\r\nEND:VALARM\r\nLOCATION:Room\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val e = Ics.read(ics).single()
        assertEquals("Real", e.description); assertEquals("Room", e.location); assertEquals("Meet", e.title)
    }

    @Test
    fun remindersAndExdatesRoundTrip() {
        val day = 86_400_000L
        val start = 1_700_000_000_000 / 3600_000 * 3600_000
        val e = IcsEvent("Weekly", start = start, end = start + 3600_000, rrule = "FREQ=WEEKLY", reminders = listOf(10, 1440), exdates = listOf(start + 7 * day, start + 14 * day))
        val back = Ics.read(Ics.write("x", listOf(e))).single()
        assertEquals(listOf(10, 1440), back.reminders)
        assertEquals(e.exdates, back.exdates)
    }

    @Test
    fun allDayExdatesAndExdateParsing() {
        val d = 1_700_000_000_000 / 86_400_000 * 86_400_000
        val e = IcsEvent("Y", start = d, end = d + 86_400_000, allDay = true, rrule = "FREQ=DAILY", exdates = listOf(d + 86_400_000))
        assertEquals(e.exdates, Ics.read(Ics.write("x", listOf(e))).single().exdates)
        assertEquals(listOf(1_704_103_200_000), Ics.parseExdates("20240101T100000Z", false))
        assertEquals("20240101T100000Z", Ics.formatExdates(listOf(1_704_103_200_000), false))
    }

    @Test
    fun triggers() {
        assertEquals(10, Ics.triggerMinutes("TRIGGER", "-PT10M")); assertEquals(0, Ics.triggerMinutes("TRIGGER", "PT0S"))
        assertEquals(null, Ics.triggerMinutes("TRIGGER", "PT5M")); assertEquals(1440, Ics.triggerMinutes("TRIGGER", "-P1D"))
    }
}
