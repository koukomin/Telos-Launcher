package de.mm20.launcher2.comms.sms

import android.content.Context
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

/** Plain JSON dump of SMS (not MMS) for backup/restore while Telos is the default SMS app. */
object SmsHistory {
    fun exportJson(context: Context): String {
        val arr = JSONArray()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ),
            null, null, "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                arr.put(
                    JSONObject()
                        .put("a", c.getString(0).orEmpty())
                        .put("b", c.getString(1).orEmpty())
                        .put("d", c.getLong(2))
                        .put("t", c.getInt(3))
                        .put("r", c.getInt(4)),
                )
            }
        }
        return JSONObject().put("v", 1).put("sms", arr).toString()
    }

    fun importJson(context: Context, json: String): Int {
        if (!SmsRole.isDefault(context)) return 0
        val arr = JSONObject(json).optJSONArray("sms") ?: return 0
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val address = o.optString("a")
            val body = o.optString("b")
            if (address.isBlank() || body.isBlank()) continue
            val type = o.optInt("t", Telephony.Sms.MESSAGE_TYPE_INBOX)
            if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                SmsStore.insertInbox(context, address, body, o.optLong("d"))
            } else {
                SmsStore.insertOutgoing(context, address, body)
            }
            n++
        }
        return n
    }
}
