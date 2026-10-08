package de.mm20.launcher2.ui.calendar

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

data class DeviceCalendar(
    val id: Long, val name: String, val account: String, val accountType: String,
    val color: Int, val visible: Boolean, val writable: Boolean,
) { val isLocal: Boolean get() = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL }

data class CalEvent(
    val id: Long, val calendarId: Long, val title: String, val location: String, val description: String,
    val begin: Long, val end: Long, val allDay: Boolean, val color: Int, val rrule: String? = null,
) {
    val firstDay: LocalDate get() = day(begin)
    /** The last day the event touches (all day events end on the day before their exclusive end). */
    val lastDay: LocalDate get() = if (allDay) maxOf(firstDay, day(end - 1)) else maxOf(firstDay, localDay(end - 1))
    private fun day(ms: Long) = if (allDay) java.time.Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate() else localDay(ms)
    private fun localDay(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
}

/** The calendars of the phone (CalendarContract): the local one, Google, CalDAV through DAVx5, Exchange and others. */
class CalendarRepository(private val context: Context) {
    private val cr: ContentResolver get() = context.contentResolver

    fun calendars(): List<DeviceCalendar> {
        val out = mutableListOf<DeviceCalendar>()
        cr.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_COLOR, Calendars.VISIBLE, Calendars.CALENDAR_ACCESS_LEVEL), null, null,
            "${Calendars.ACCOUNT_NAME} COLLATE NOCASE, ${Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE")?.use { c ->
            while (c.moveToNext()) out += DeviceCalendar(
                c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getInt(4),
                c.getInt(5) == 1, c.getInt(6) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
            )
        }
        return out
    }

    fun setVisible(id: Long, visible: Boolean) {
        cr.update(ContentUris.withAppendedId(Calendars.CONTENT_URI, id), ContentValues().apply { put(Calendars.VISIBLE, if (visible) 1 else 0) }, null, null)
    }

    /** Creates a calendar that is only on this phone (account type LOCAL, never synced). */
    fun createLocalCalendar(name: String, color: Int): Long {
        val uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, LocalAccount)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        val v = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, LocalAccount); put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, name); put(Calendars.CALENDAR_DISPLAY_NAME, name); put(Calendars.CALENDAR_COLOR, color)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER); put(Calendars.OWNER_ACCOUNT, LocalAccount)
            put(Calendars.VISIBLE, 1); put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        return ContentUris.parseId(cr.insert(uri, v)!!)
    }

    fun deleteLocalCalendar(id: Long) {
        val uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, id).buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, LocalAccount)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        cr.delete(uri, null, null)
    }

    /** The events of the visible calendars that touch the days from [from] to [to] (inclusive), repeating events expanded. */
    fun events(from: LocalDate, to: LocalDate, calendars: Collection<DeviceCalendar>): List<CalEvent> {
        val visible = calendars.filter { it.visible }.associateBy { it.id }
        if (visible.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val b = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val e = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, b); ContentUris.appendId(it, e) }.build()
        val out = mutableListOf<CalEvent>()
        cr.query(uri, arrayOf(Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.EVENT_LOCATION, Instances.DESCRIPTION,
            Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.EVENT_COLOR, Instances.RRULE), null, null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val cal = visible[c.getLong(1)] ?: continue
                val ev = CalEvent(c.getLong(0), cal.id, c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty(),
                    c.getLong(5), c.getLong(6), c.getInt(7) == 1, c.getInt(8).let { if (it != 0) it else cal.color }, c.getString(9))
                if (ev.lastDay >= from && ev.firstDay <= to) out += ev
            }
        }
        return out
    }

    fun save(
        id: Long?, calendarId: Long, title: String, description: String, location: String,
        start: Long, end: Long, allDay: Boolean, rrule: String?, reminderMinutes: Int?,
    ): Long {
        // The editor shows one reminder. If it is unchanged, the reminders of the event (several, or the calendar's default) stay as they are.
        val existingReminders = if (id != null) remindersOf(id) else emptyList()
        val keepReminders = id != null && existingReminders.firstOrNull { it >= 0 } == reminderMinutes
        val v = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId); put(Events.TITLE, title); put(Events.DESCRIPTION, description)
            put(Events.EVENT_LOCATION, location); put(Events.ALL_DAY, if (allDay) 1 else 0)
            put(Events.EVENT_TIMEZONE, if (allDay) "UTC" else TimeZone.getDefault().id)
            put(Events.DTSTART, start)
            if (rrule.isNullOrBlank()) {
                put(Events.DTEND, end); putNull(Events.RRULE); putNull(Events.DURATION)
            } else {
                put(Events.RRULE, rrule); putNull(Events.DTEND)
                val secs = (end - start) / 1000
                put(Events.DURATION, if (allDay) "P${maxOf(1, secs / 86400)}D" else "PT${maxOf(secs, 0)}S")
            }
            put(Events.HAS_ALARM, if (!keepReminders && reminderMinutes != null || keepReminders && existingReminders.isNotEmpty()) 1 else 0)
        }
        val eventId = if (id == null) ContentUris.parseId(cr.insert(Events.CONTENT_URI, v)!!)
        else { cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), v, null, null); id }
        if (!keepReminders) cr.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()))
        if (!keepReminders && reminderMinutes != null) cr.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, eventId); put(Reminders.MINUTES, reminderMinutes); put(Reminders.METHOD, Reminders.METHOD_ALERT)
        })
        return eventId
    }

    private fun remindersOf(eventId: Long): List<Int> = cr.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES), "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()), null)
        ?.use { c -> generateSequence { if (c.moveToNext()) c.getInt(0) else null }.toList() }.orEmpty()

    /** The start of the event itself (of the first occurrence for repeating events), as stored. */
    fun seriesStart(eventId: Long): Long? = cr.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events.DTSTART), null, null, null)
        ?.use { if (it.moveToFirst()) it.getLong(0) else null }

    fun reminderOf(eventId: Long): Int? = cr.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES), "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()), null)
        ?.use { if (it.moveToFirst()) it.getInt(0).takeIf { m -> m >= 0 } else null }

    fun delete(id: Long) { cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null) }

    /** All events of one calendar as stored (not expanded), for export and backup. */
    fun rawEvents(calendarId: Long): List<IcsEvent> {
        val out = mutableListOf<IcsEvent>()
        cr.query(Events.CONTENT_URI, arrayOf(Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION, Events.DTSTART, Events.DTEND,
            Events.DURATION, Events.ALL_DAY, Events.RRULE), "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0", arrayOf(calendarId.toString()), Events.DTSTART)?.use { c ->
            while (c.moveToNext()) {
                val start = c.getLong(3)
                val allDay = c.getInt(6) == 1
                val end = if (!c.isNull(4)) c.getLong(4) else start + Ics.durationSeconds(c.getString(5).orEmpty()) * 1000
                out += IcsEvent(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2).orEmpty(), start, maxOf(end, start), allDay, c.getString(7))
            }
        }
        return out
    }

    /** Adds the events that the calendar does not have yet (same title and start). Returns how many were added. */
    fun importEvents(calendarId: Long, events: List<IcsEvent>): Int {
        val have = rawEvents(calendarId).map { it.title to it.start }.toMutableSet()
        var n = 0
        for (e in events) {
            if (!have.add(e.title to e.start)) continue
            save(null, calendarId, e.title, e.description, e.location, e.start, e.end, e.allDay, e.rrule, null)
            n++
        }
        return n
    }

    /** Asks the system to sync the accounts that have calendars. The sync itself is done by the account's sync adapter. */
    fun requestSync() {
        calendars().filter { !it.isLocal }.map { it.account to it.accountType }.distinct().forEach { (name, type) ->
            ContentResolver.requestSync(Account(name, type), CalendarContract.AUTHORITY, Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true); putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            })
        }
    }

    companion object { const val LocalAccount = "Telos" }
}
