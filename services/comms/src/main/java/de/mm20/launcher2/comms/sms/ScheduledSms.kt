package de.mm20.launcher2.comms.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

data class ScheduledSms(val id: Long, val number: String, val body: String, val atEpochMs: Long, val subId: Int = -1)

object ScheduledSmsStore {
    private const val PREFS = "telos_scheduled_sms"
    private const val KEY = "items"

    fun list(context: Context): List<ScheduledSms> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ScheduledSms(o.getLong("id"), o.getString("number"), o.getString("body"), o.getLong("at"), o.optInt("sub", -1))
            }.sortedBy { it.atEpochMs }
        }.getOrDefault(emptyList()) // unreadable data must not crash the list
    }

    /** Alarms are gone after a restart: set them again. A message that is overdue goes out shortly. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        list(context).forEach { schedule(context, it.copy(atEpochMs = maxOf(it.atEpochMs, now + 5_000))) }
    }

    @Synchronized
    fun add(context: Context, number: String, body: String, atEpochMs: Long, subId: Int = -1): ScheduledSms {
        val item = ScheduledSms(System.currentTimeMillis(), number, body, atEpochMs, subId)
        val next = list(context) + item
        persist(context, next)
        schedule(context, item)
        return item
    }

    @Synchronized
    fun remove(context: Context, id: Long) {
        // a removed message must not go out when its alarm fires
        list(context).firstOrNull { it.id == id }?.let { item ->
            context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, item))
        }
        persist(context, list(context).filter { it.id != id })
    }

    private fun persist(context: Context, items: List<ScheduledSms>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("number", it.number).put("body", it.body).put("at", it.atEpochMs).put("sub", it.subId))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    /** On Android 12 and later the user has to allow exact alarms (Settings, Alarms & reminders) */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    private fun schedule(context: Context, item: ScheduledSms) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, item)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.atEpochMs, pi)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // without the "alarms and reminders" permission the system may delay it by minutes
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.atEpochMs, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, item.atEpochMs, pi)
        }
    }

    private fun pending(context: Context, item: ScheduledSms): PendingIntent {
        val intent = Intent(context, ScheduledSmsReceiver::class.java)
            .putExtra("id", item.id)
            .putExtra("number", item.number)
            .putExtra("body", item.body)
            .putExtra("sub", item.subId)
        return PendingIntent.getBroadcast(
            context,
            (item.id xor (item.id ushr 32)).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class ScheduledSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra("number").orEmpty()
        val body = intent.getStringExtra("body").orEmpty()
        val id = intent.getLongExtra("id", 0L)
        // the SIM may have been removed since: then the system default sends it
        val chosen = intent.getIntExtra("sub", -1)
        val sims = SmsSims.active(context)
        val subId = if (sims.isNotEmpty() && sims.none { it.subId == chosen }) -1 else chosen
        // when it could not be sent (no permission) it stays in the list instead of vanishing
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        // SmsThreads keeps the sent message in the conversation (system store or Telos' own copy), QuickSms only sends
        val sent = if (granted) SmsThreads.send(context, number, body, subId) else QuickSms.send(context, number, body)
        if (sent) ScheduledSmsStore.remove(context, id)
    }
}

/** Sets the alarms of the scheduled messages again after a restart. */
class ScheduledSmsBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ScheduledSmsStore.rescheduleAll(context)
    }
}
