package de.mm20.launcher2.ui.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** An event as it is written to and read from an .ics file. Times are milliseconds, [end] is exclusive for all day events. */
data class IcsEvent(
    val title: String,
    val description: String = "",
    val location: String = "",
    val start: Long,
    val end: Long,
    val allDay: Boolean = false,
    val rrule: String? = null,
    /** Reminders in minutes before the start. */
    val reminders: List<Int> = emptyList(),
    /** Start times (milliseconds) of the occurrences of a repeating event that were deleted (EXDATE). */
    val exdates: List<Long> = emptyList(),
)

/** A small iCalendar (RFC 5545) reader and writer for VEVENT, enough to move events between calendars. */
object Ics {
    private val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val date = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")
    private fun unesc(s: String): String {
        val sb = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]; sb.append(if (n == 'n' || n == 'N') '\n' else n); i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private fun fold(line: String): String {
        if (line.length <= 73) return line
        val sb = StringBuilder()
        var i = 0
        var max = 73
        while (i < line.length) {
            var end = minOf(i + max, line.length)
            // never cut a surrogate pair (emoji) in two
            if (end < line.length && Character.isHighSurrogate(line[end - 1])) end--
            if (i > 0) sb.append("\r\n ")
            sb.append(line, i, end)
            i = end
            max = 72
        }
        return sb.toString()
    }

    fun write(name: String, events: List<IcsEvent>): String {
        val sb = StringBuilder()
        fun line(s: String) = sb.append(fold(s)).append("\r\n")
        line("BEGIN:VCALENDAR"); line("VERSION:2.0"); line("PRODID:-//Telos Launcher//Telos Calendar//EN"); line("X-WR-CALNAME:${esc(name)}")
        val stamp = utc.format(Instant.now())
        for ((i, e) in events.withIndex()) {
            line("BEGIN:VEVENT")
            line("UID:${e.start}-$i-${e.title.hashCode()}@telos")
            line("DTSTAMP:$stamp")
            if (!e.allDay && !e.rrule.isNullOrBlank()) {
                // A repeating event keeps its wall clock time across daylight saving changes only with a time zone
                val z = ZoneId.systemDefault()
                val local = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(z)
                line("DTSTART;TZID=${z.id}:${local.format(Instant.ofEpochMilli(e.start))}"); line("DTEND;TZID=${z.id}:${local.format(Instant.ofEpochMilli(e.end))}")
            } else if (e.allDay) { line("DTSTART;VALUE=DATE:${date.format(Instant.ofEpochMilli(e.start))}"); line("DTEND;VALUE=DATE:${date.format(Instant.ofEpochMilli(e.end))}") }
            else { line("DTSTART:${utc.format(Instant.ofEpochMilli(e.start))}"); line("DTEND:${utc.format(Instant.ofEpochMilli(e.end))}") }
            line("SUMMARY:${esc(e.title)}")
            if (e.description.isNotEmpty()) line("DESCRIPTION:${esc(e.description)}")
            if (e.location.isNotEmpty()) line("LOCATION:${esc(e.location)}")
            e.rrule?.let { line("RRULE:$it") }
            if (e.rrule != null && e.exdates.isNotEmpty()) {
                if (e.allDay) line("EXDATE;VALUE=DATE:${e.exdates.joinToString(",") { date.format(Instant.ofEpochMilli(it)) }}")
                else {
                    val z = ZoneId.systemDefault()
                    val local = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(z)
                    line("EXDATE;TZID=${z.id}:${e.exdates.joinToString(",") { local.format(Instant.ofEpochMilli(it)) }}")
                }
            }
            for (m in e.reminders.distinct().filter { it >= 0 }) {
                line("BEGIN:VALARM"); line("ACTION:DISPLAY"); line("DESCRIPTION:Reminder"); line("TRIGGER:-PT${m}M"); line("END:VALARM")
            }
            line("END:VEVENT")
        }
        line("END:VCALENDAR")
        return sb.toString()
    }

    fun read(text: String): List<IcsEvent> {
        val lines = ArrayList<String>()
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) lines[lines.size - 1] = lines.last() + raw.substring(1)
            else lines += raw
        }
        val out = mutableListOf<IcsEvent>()
        var cur: MutableMap<String, Pair<Map<String, String>, String>>? = null
        var alarms = mutableListOf<Int>()
        var exdates = mutableListOf<Long>()
        var inAlarm = false
        var nested = 0 // depth inside a component of the event (VALARM), whose properties are not the event's
        for (l in lines) {
            when {
                l.equals("BEGIN:VEVENT", true) -> { cur = mutableMapOf(); nested = 0; alarms = mutableListOf(); exdates = mutableListOf(); inAlarm = false }
                l.equals("END:VEVENT", true) -> { cur?.let { toEvent(it, alarms, exdates)?.let(out::add) }; cur = null }
                cur != null && l.startsWith("BEGIN:", true) -> { nested++; if (l.equals("BEGIN:VALARM", true)) inAlarm = true }
                cur != null && l.startsWith("END:", true) -> { if (nested > 0) nested--; if (l.equals("END:VALARM", true)) inAlarm = false }
                cur != null && nested > 0 && inAlarm && l.startsWith("TRIGGER", true) && l.contains(':') ->
                    triggerMinutes(l.substringBefore(':'), l.substringAfter(':'))?.let(alarms::add)
                cur != null && nested == 0 && l.startsWith("EXDATE", true) && l.contains(':') -> {
                    val params = l.substringBefore(':').split(';').drop(1).associate { it.substringBefore('=').uppercase() to it.substringAfter('=', "") }
                    l.substringAfter(':').split(',').forEach { v -> parseTime(params, v.trim())?.let { exdates += it.first } }
                }
                cur != null && nested == 0 && l.contains(':') -> {
                    val head = l.substringBefore(':'); val value = l.substringAfter(':')
                    val parts = head.split(';')
                    val params = parts.drop(1).associate { it.substringBefore('=').uppercase() to it.substringAfter('=', "") }
                    cur[parts[0].uppercase()] = params to value
                }
            }
        }
        return out
    }

    private fun parseTime(params: Map<String, String>, v: String): Pair<Long, Boolean>? = try {
        if (params["VALUE"] == "DATE" || v.length == 8) Pair(LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), true)
        else {
            val ldt = LocalDateTime.parse(v.removeSuffix("Z"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            val zone = if (v.endsWith("Z")) ZoneOffset.UTC else params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
            Pair(ldt.atZone(zone).toInstant().toEpochMilli(), false)
        }
    } catch (e: Exception) { null }

    /** Seconds of an RFC 5545 or Android duration such as P1D, PT1H30M or P3600S. */
    fun durationSeconds(d: String): Long {
        val m = Regex("P(?:(\\d+)W)?(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").matchEntire(d.trim()) ?: return 0
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0
        return g(1) * 604800 + g(2) * 86400 + g(3) * 3600 + g(4) * 60 + g(5)
    }

    /** The EXDATE value of Android's calendar storage (comma separated, UTC) as milliseconds. */
    fun parseExdates(v: String, allDay: Boolean): List<Long> =
        v.split(',').mapNotNull { x -> x.trim().takeIf { it.isNotEmpty() }?.let { parseTime(if (allDay) mapOf("VALUE" to "DATE") else emptyMap(), if (!allDay && !it.endsWith("Z") && it.length > 8) it + "Z" else it)?.first } }

    fun formatExdates(ms: List<Long>, allDay: Boolean): String =
        ms.joinToString(",") { (if (allDay) date else utc).format(Instant.ofEpochMilli(it)) }

    /** Minutes before the start for a relative alarm trigger such as -PT10M, null for triggers after the start or at absolute times. */
    internal fun triggerMinutes(head: String, v: String): Int? {
        if (head.uppercase().contains("VALUE=DATE-TIME") || head.uppercase().contains("RELATED=END")) return null
        val t = v.trim()
        val before = t.startsWith("-")
        val secs = durationSeconds(t.removePrefix("-").removePrefix("+"))
        if (!before && secs > 0) return null
        if (!t.removePrefix("-").removePrefix("+").startsWith("P")) return null
        return (secs / 60).toInt()
    }

    private fun toEvent(p: Map<String, Pair<Map<String, String>, String>>, alarms: List<Int>, exdates: List<Long>): IcsEvent? {
        val (sp, sv) = p["DTSTART"] ?: return null
        val (start, allDay) = parseTime(sp, sv) ?: return null
        val end = p["DTEND"]?.let { (ep, ev) -> parseTime(ep, ev)?.first }
            ?: p["DURATION"]?.let { start + durationSeconds(it.second) * 1000 }
            ?: if (allDay) start + 86_400_000 else start
        return IcsEvent(
            title = unesc(p["SUMMARY"]?.second.orEmpty()), description = unesc(p["DESCRIPTION"]?.second.orEmpty()),
            location = unesc(p["LOCATION"]?.second.orEmpty()), start = start, end = maxOf(end, start), allDay = allDay,
            rrule = p["RRULE"]?.second, reminders = alarms.distinct(),
            exdates = if (p["RRULE"] != null) exdates.distinct() else emptyList(),
        )
    }
}
