package de.mm20.launcher2.comms.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

data class ScheduledSms(val id: Long, val number: String, val body: String, val atEpochMs: Long)

object ScheduledSmsStore {
    private const val PREFS = "telos_scheduled_sms"
    private const val KEY = "items"

    fun list(context: Context): List<ScheduledSms> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ScheduledSms(o.getLong("id"), o.getString("number"), o.getString("body"), o.getLong("at"))
            }.sortedBy { it.atEpochMs }
        }.getOrDefault(emptyList()) // unreadable data must not crash the list
    }

    /** Alarms are gone after a restart: set them again. A message that is overdue goes out shortly. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        list(context).forEach { schedule(context, it.copy(atEpochMs = maxOf(it.atEpochMs, now + 5_000))) }
    }

    fun add(context: Context, number: String, body: String, atEpochMs: Long): ScheduledSms {
        val item = ScheduledSms(System.currentTimeMillis(), number, body, atEpochMs)
        val next = list(context) + item
        persist(context, next)
        schedule(context, item)
        return item
    }

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
            arr.put(JSONObject().put("id", it.id).put("number", it.number).put("body", it.body).put("at", it.atEpochMs))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    private fun schedule(context: Context, item: ScheduledSms) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, item)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        // when it could not be sent (no permission) it stays in the list instead of vanishing
        if (QuickSms.send(context, number, body)) ScheduledSmsStore.remove(context, id)
    }
}

/** Sets the alarms of the scheduled messages again after a restart. */
class ScheduledSmsBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ScheduledSmsStore.rescheduleAll(context)
    }
}
