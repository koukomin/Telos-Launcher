package de.mm20.launcher2.comms.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

/** One-shot alarms. Persist before scheduling so process death and updates do not lose them. */
data class ClockAlarm(
    val id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    val soundUri: String = "",
    val soundName: String = "",
)

object ClockAlarms {
    private fun prefs(context: Context) = context.getSharedPreferences("telos_clock_alarms", Context.MODE_PRIVATE)
    fun load(context: Context): List<ClockAlarm> = runCatching {
        val array = JSONArray(prefs(context).getString("alarms", "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let {
            ClockAlarm(it.getInt("id"), it.getInt("hour"), it.getInt("minute"), it.optBoolean("enabled", true),
                it.optString("label"), it.optString("uri"), it.optString("sound"))
        }}
    }.getOrDefault(emptyList())

    fun canSchedule(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun save(context: Context, alarms: List<ClockAlarm>) {
        val previous = load(context)
        val array = JSONArray().apply { alarms.forEach { alarm -> put(JSONObject().apply {
            put("id", alarm.id); put("hour", alarm.hour); put("minute", alarm.minute)
            put("enabled", alarm.enabled); put("label", alarm.label); put("uri", alarm.soundUri); put("sound", alarm.soundName)
        }) } }
        prefs(context).edit().putString("alarms", array.toString()).commit()
        val manager = context.getSystemService(AlarmManager::class.java)
        previous.forEach { manager.cancel(pending(context, it.id)) }
        if (canSchedule(context)) alarms.filter { it.enabled }.forEach { alarm ->
            val now = ZonedDateTime.now()
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(nextOccurrence(now, alarm.hour, alarm.minute), PendingIntent.getActivity(context, 7141, context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)), pending(context, alarm.id))
        }
    }

    fun nextOccurrence(now: ZonedDateTime, hour: Int, minute: Int): Long {
        val candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        return (if (candidate.isAfter(now)) candidate else candidate.plusDays(1)).toInstant().toEpochMilli()
    }

    private fun pending(context: Context, id: Int) = PendingIntent.getBroadcast(context, id,
        Intent(context, ClockAlarmReceiver::class.java).setAction("telos.clock.FIRE").putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

class ClockAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "telos.clock.FIRE") {
            ClockAlarms.save(context, ClockAlarms.load(context))
            return
        }
        val id = intent.getIntExtra("id", -1)
        val alarms = ClockAlarms.load(context)
        val alarm = alarms.firstOrNull { it.id == id && it.enabled } ?: return
        ClockAlarms.save(context, alarms.map { if (it.id == id) it.copy(enabled = false) else it })
        ContextCompat.startForegroundService(context, Intent(context, ClockAlarmService::class.java)
            .putExtra("label", alarm.label).putExtra("uri", alarm.soundUri))
    }
}
