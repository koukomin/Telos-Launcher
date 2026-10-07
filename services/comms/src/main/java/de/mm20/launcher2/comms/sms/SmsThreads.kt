package de.mm20.launcher2.comms.sms

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

data class SmsConversation(
    val threadId: Long,
    val address: String,
    val name: String?,
    val snippet: String,
    val date: Long,
    val unread: Int,
)

data class SmsMessage(
    val id: String,
    val body: String,
    val date: Long,
    val outgoing: Boolean,
)

/**
 * Reads the text messages of the phone (the system keeps them, Telos is not the default SMS app)
 * and keeps a copy of what is sent from here, because the system does not store messages that
 * another app sends.
 */
object SmsThreads {

    /** One entry per conversation, newest first. Needs the READ_SMS permission. */
    fun conversations(context: Context): List<SmsConversation> = runCatching {
        val byThread = LinkedHashMap<Long, SmsConversation>()
        val unread = HashMap<Long, Int>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.TYPE),
            null, null, "${Telephony.Sms.DATE} DESC LIMIT 3000",
        )?.use { c ->
            while (c.moveToNext()) {
                val thread = c.getLong(0)
                if (c.getInt(4) == 0 && c.getInt(5) == Telephony.Sms.MESSAGE_TYPE_INBOX) unread[thread] = (unread[thread] ?: 0) + 1
                if (thread !in byThread) {
                    val address = c.getString(1).orEmpty()
                    byThread[thread] = SmsConversation(thread, address, null, c.getString(2).orEmpty(), c.getLong(3), 0)
                }
            }
        }
        // what was sent from Telos to a number that has no conversation yet (negative ids)
        var nextId = -1L
        val known = byThread.values.map { it.address }
        SentLog.all(context).groupBy { it.first }.forEach { (address, sent) ->
            if (known.none { de.mm20.launcher2.comms.PhoneNumbers.match(it, address) }) {
                val last = sent.maxByOrNull { it.second.date }!!.second
                byThread[nextId] = SmsConversation(nextId, address, null, last.body, last.date, 0)
                nextId--
            }
        }
        byThread.values.map { it.copy(name = displayName(context, it.address), unread = unread[it.threadId] ?: 0) }
            .sortedByDescending { it.date }
    }.getOrDefault(emptyList())

    /** The messages of one conversation, oldest first, with what was sent from Telos mixed in. */
    fun messages(context: Context, conversation: SmsConversation): List<SmsMessage> = runCatching {
        val out = mutableListOf<SmsMessage>()
        if (conversation.threadId >= 0) {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(conversation.threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 500",
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(3)
                    out += SmsMessage("s${c.getLong(0)}", c.getString(1).orEmpty(), c.getLong(2), type != Telephony.Sms.MESSAGE_TYPE_INBOX)
                }
            }
        }
        SentLog.all(context)
            .filter { de.mm20.launcher2.comms.PhoneNumbers.match(it.first, conversation.address) }
            .forEach { out += it.second }
        out.sortedBy { it.date }
    }.getOrDefault(emptyList())

    private fun displayName(context: Context, number: String): String? = runCatching {
        if (number.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    /** Sends a message and remembers it. Returns false when it could not be sent. */
    fun send(context: Context, address: String, text: String): Boolean {
        if (address.isBlank() || text.isBlank()) return false
        val sent = SmsRepository(context).sendSms(address, text.trim())
        if (sent) SentLog.add(context, address, text.trim())
        return sent
    }
}

/** The messages sent from Telos, newest last, at most 500. Kept inside the app's private files. */
internal object SentLog {
    private const val PREFS = "telos_sent_sms"
    private const val KEY = "items"

    fun all(context: Context): List<Pair<String, SmsMessage>> = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            o.getString("a") to SmsMessage("t${o.getLong("d")}", o.getString("b"), o.getLong("d"), true)
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(context: Context, address: String, body: String) {
        val list = all(context).takeLast(499) + (address to SmsMessage("t", body, System.currentTimeMillis(), true))
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("a", it.first).put("b", it.second.body).put("d", it.second.date)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
