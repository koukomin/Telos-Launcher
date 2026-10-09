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

data class SmsAttachment(val uri: String, val mimeType: String)

data class SmsMessage(
    val id: String,
    val body: String,
    val date: Long,
    val outgoing: Boolean,
    val attachments: List<SmsAttachment> = emptyList(),
    val failed: Boolean = false,
)

/**
 * Reads the text messages of the phone (the system keeps them, Telos is not the default SMS app)
 * and keeps a copy of what is sent from here, because the system does not store messages that
 * another app sends.
 */
object SmsThreads {

    /** One entry per conversation, newest first. Needs the READ_SMS permission. */
    fun conversations(context: Context): List<SmsConversation> {
        val fromThreads = conversationsFromThreads(context)
        return fromThreads.ifEmpty { conversationsFromMessages(context) }.let { withSentOnly(context, it) }
    }

    /** The conversation list of the system, which includes multimedia messages. */
    private fun conversationsFromThreads(context: Context): List<SmsConversation> = runCatching {
        val addresses = HashMap<Long, String>()
        context.contentResolver.query(Uri.parse("content://mms-sms/canonical-addresses"), null, null, null, null)?.use { c ->
            val id = c.getColumnIndex("_id")
            val address = c.getColumnIndex("address")
            while (c.moveToNext()) addresses[c.getLong(id)] = c.getString(address).orEmpty()
        }
        val out = mutableListOf<SmsConversation>()
        val uri = Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build()
        context.contentResolver.query(uri, null, null, null, "date DESC")?.use { c ->
            val id = c.getColumnIndex("_id")
            val date = c.getColumnIndex("date")
            val snippet = c.getColumnIndex("snippet")
            val read = c.getColumnIndex("read")
            val recipients = c.getColumnIndex("recipient_ids")
            val attachment = c.getColumnIndex("has_attachment")
            while (c.moveToNext()) {
                val numbers = c.getString(recipients).orEmpty().split(' ').mapNotNull { it.toLongOrNull() }.mapNotNull { addresses[it] }
                if (numbers.isEmpty()) continue
                val text = c.getString(snippet).orEmpty().ifBlank { if (attachment >= 0 && c.getInt(attachment) > 0) SmsText.get(context, "au_messages_attachment", "Attachment") else "" }
                out += SmsConversation(c.getLong(id), numbers.joinToString(", "), null, text, c.getLong(date), if (c.getInt(read) == 0) 1 else 0)
            }
        }
        out.map { it.copy(name = if (',' in it.address) null else displayName(context, it.address)) }
    }.getOrDefault(emptyList())

    private fun conversationsFromMessages(context: Context): List<SmsConversation> = runCatching {
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
        byThread.values.map { it.copy(name = displayName(context, it.address), unread = unread[it.threadId] ?: 0) }
            .sortedByDescending { it.date }
    }.getOrDefault(emptyList())

    /** What was sent from Telos (before it was the default SMS app) to a number that has no conversation yet */
    private fun withSentOnly(context: Context, list: List<SmsConversation>): List<SmsConversation> {
        var nextId = -1L
        val result = list.toMutableList()
        SentLog.all(context).groupBy { it.first }.forEach { (address, sent) ->
            if (result.none { c -> c.address.split(", ").any { de.mm20.launcher2.comms.PhoneNumbers.match(it, address) } }) {
                val last = sent.maxByOrNull { it.second.date }!!.second
                result += SmsConversation(nextId, address, displayName(context, address), last.body, last.date, 0)
                nextId--
            }
        }
        return result.sortedByDescending { it.date }
    }

    /** The messages of one conversation, oldest first, with what was sent from Telos mixed in. */
    fun messages(context: Context, conversation: SmsConversation): List<SmsMessage> = runCatching {
        val out = mutableListOf<SmsMessage>()
        val threadId = if (conversation.threadId >= 0) conversation.threadId else threadOf(context, conversation.address)
        if (threadId >= 0) {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 500",
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(3)
                    out += SmsMessage(
                        "s${c.getLong(0)}", c.getString(1).orEmpty(), c.getLong(2), type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                        failed = type == Telephony.Sms.MESSAGE_TYPE_FAILED,
                    )
                }
            }
        }
        if (threadId >= 0) out += mmsMessages(context, threadId)
        SentLog.all(context)
            .filter { de.mm20.launcher2.comms.PhoneNumbers.match(it.first, conversation.address) }
            .forEach { out += it.second }
        out.sortedBy { it.date }
    }.getOrDefault(emptyList())

    /**
     * The thread of a number that has no conversation yet (a message was just written to it). Only the
     * default SMS app may create it; otherwise there is none and -1 is returned.
     */
    private fun threadOf(context: Context, address: String): Long = runCatching {
        val numbers = address.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (numbers.isEmpty() || !SmsRole.isDefault(context)) -1L
        else Telephony.Threads.getOrCreateThreadId(context, numbers)
    }.getOrDefault(-1L)

    private fun mmsMessages(context: Context, threadId: Long): List<SmsMessage> = runCatching {
        val out = mutableListOf<SmsMessage>()
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX),
            "${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Mms.DATE} DESC LIMIT 200",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val box = c.getInt(2)
                var text = ""
                val attachments = mutableListOf<SmsAttachment>()
                context.contentResolver.query(
                    Uri.parse("content://mms/part"), arrayOf("_id", "ct", "text"), "mid = ?", arrayOf(id.toString()), null,
                )?.use { p ->
                    while (p.moveToNext()) {
                        val type = p.getString(1).orEmpty()
                        when {
                            type == "text/plain" -> text += p.getString(2).orEmpty()
                            type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/") ->
                                attachments += SmsAttachment("content://mms/part/${p.getLong(0)}", type)
                        }
                    }
                }
                out += SmsMessage(
                    "m$id", text, c.getLong(1) * 1000, box != Telephony.Mms.MESSAGE_BOX_INBOX, attachments,
                    failed = box == Telephony.Mms.MESSAGE_BOX_FAILED,
                )
            }
        }
        out
    }.getOrDefault(emptyList())

    internal fun displayName(context: Context, number: String): String? = runCatching {
        if (number.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    /**
     * Sends a message. As the default SMS app it goes into the system's message store, otherwise a
     * copy is kept by Telos (the system does not store what another app sends). Returns false when
     * it could not be sent.
     */
    fun send(context: Context, address: String, text: String): Boolean {
        if (address.isBlank() || text.isBlank()) return false
        val row = SmsStore.insertOutgoing(context, address, text.trim())
        val sentIntent = row?.let {
            android.app.PendingIntent.getBroadcast(
                context, it.hashCode(),
                android.content.Intent(context, SmsSentReceiver::class.java).setData(it),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (android.os.Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0),
            )
        }
        val sent = SmsRepository(context).sendSms(address, text.trim(), sentIntent)
        if (!sent) row?.let { SmsStore.setType(context, it, Telephony.Sms.MESSAGE_TYPE_FAILED) }
        if (sent && row == null) SentLog.add(context, address, text.trim())
        return sent
    }

    /** Sends a multimedia message: [text] and the pictures or videos at [attachments]. Default SMS app only. */
    fun sendMms(context: Context, addresses: List<String>, text: String, attachments: List<Uri>): Boolean {
        if (!SmsRole.isDefault(context) || addresses.isEmpty()) return false
        val parts = mutableListOf<MmsPart>()
        if (text.isNotBlank()) parts += MmsPart("text/plain", "text.txt", text.trim().toByteArray(Charsets.UTF_8))
        attachments.forEachIndexed { i, uri -> MmsMedia.read(context, uri, i)?.let { parts += it } }
        if (parts.isEmpty()) return false
        val row = MmsStore.insertOutgoing(context, addresses, parts)
        val ok = MmsTransport.send(context, addresses, parts, row)
        if (!ok) row?.let { MmsStore.setBox(context, it, Telephony.Mms.MESSAGE_BOX_FAILED) }
        return ok
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
