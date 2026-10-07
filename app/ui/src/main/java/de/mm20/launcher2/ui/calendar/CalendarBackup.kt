package de.mm20.launcher2.ui.calendar

import android.content.Context
import de.mm20.launcher2.backup.BackupGroup
import de.mm20.launcher2.backup.Backupable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Backs up the calendars that live on the phone (account type LOCAL) as .ics files. Calendars that are
 * synced from an account (Google, CalDAV, Exchange) are not in the backup: the account has them.
 */
class CalendarBackup(private val context: Context) : Backupable {
    override val group: BackupGroup = BackupGroup.Calendar

    override suspend fun backup(toDir: File) = withContext(Dispatchers.IO) {
        val repo = CalendarRepository(context)
        val dir = File(toDir, "calendar").also { it.mkdirs() }
        val list = JSONArray()
        try {
            repo.calendars().filter { it.isLocal }.forEachIndexed { i, cal ->
                File(dir, "$i.ics").writeText(Ics.write(cal.name, repo.rawEvents(cal.id)))
                list.put(JSONObject().put("file", "$i.ics").put("name", cal.name).put("color", cal.color))
            }
        } catch (e: SecurityException) { /* no calendar permission, nothing to back up */ }
        File(dir, "calendars.json").writeText(list.toString())
    }

    override suspend fun restore(fromDir: File) = withContext(Dispatchers.IO) {
        val dir = File(fromDir, "calendar")
        val meta = File(dir, "calendars.json").takeIf { it.exists() } ?: return@withContext
        val repo = CalendarRepository(context)
        try {
            val list = JSONArray(meta.readText())
            val existing = repo.calendars().filter { it.isLocal }
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                val id = existing.firstOrNull { it.name == o.getString("name") }?.id ?: repo.createLocalCalendar(o.getString("name"), o.optInt("color", 0xFF1E88E5.toInt()))
                val events = Ics.read(File(dir, o.getString("file")).readText())
                repo.importEvents(id, events)
            }
        } catch (e: SecurityException) { /* no calendar permission */ }
    }
}
