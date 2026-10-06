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
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ScheduledSms(o.getLong("id"), o.getString("number"), o.getString("body"), o.getLong("at"))
        }.sortedBy { it.atEpochMs }
    }

    fun add(context: Context, number: String, body: String, atEpochMs: Long): ScheduledSms {
        val item = ScheduledSms(System.currentTimeMillis(), number, body, atEpochMs)
        val next = list(context) + item
        persist(context, next)
        schedule(context, item)
        return item
    }

    fun remove(context: Context, id: Long) {
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
            item.id.toInt(),
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
        QuickSms.send(context, number, body)
        ScheduledSmsStore.remove(context, id)
    }
}
