package de.mm20.launcher2.comms.sms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony

/** Writes multimedia messages to the system's message store (default SMS app only). */
object MmsStore {

    fun insertIncoming(context: Context, message: MmsMessage, transactionId: String?, fallbackFrom: String?): Uri? {
        if (!SmsRole.isDefault(context)) return null
        val from = message.from ?: fallbackFrom ?: return null
        return runCatching {
            val threadId = Telephony.Threads.getOrCreateThreadId(context, from)
            val id = insertMessage(
                context, threadId, Telephony.Mms.MESSAGE_BOX_INBOX, message.subject, transactionId, message.parts,
                dateSeconds = message.dateSeconds ?: (System.currentTimeMillis() / 1000), read = 0,
            ) ?: return null
            addAddress(context, id, from, 137) // from
            ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id)
        }.getOrNull()
    }

    /** A message that is being sent, in the outbox until the result is known */
    fun insertOutgoing(context: Context, recipients: List<String>, parts: List<MmsPart>): Uri? {
        if (!SmsRole.isDefault(context) || recipients.isEmpty()) return null
        return runCatching {
            val threadId = Telephony.Threads.getOrCreateThreadId(context, recipients.toSet())
            val id = insertMessage(
                context, threadId, Telephony.Mms.MESSAGE_BOX_OUTBOX, null, null, parts,
                dateSeconds = System.currentTimeMillis() / 1000, read = 1,
            ) ?: return null
            recipients.forEach { addAddress(context, id, it, 151) } // to
            ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id)
        }.getOrNull()
    }

    fun setBox(context: Context, uri: Uri, box: Int) {
        runCatching {
            context.contentResolver.update(uri, ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, box) }, null, null)
        }
    }

    private fun insertMessage(
        context: Context, threadId: Long, box: Int, subject: String?, transactionId: String?,
        parts: List<MmsPart>, dateSeconds: Long, read: Int,
    ): Long? {
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, dateSeconds)
            put(Telephony.Mms.MESSAGE_BOX, box)
            put(Telephony.Mms.READ, read)
            put(Telephony.Mms.SEEN, read)
            put(Telephony.Mms.MESSAGE_TYPE, if (box == Telephony.Mms.MESSAGE_BOX_INBOX) 132 else 128)
            put(Telephony.Mms.MMS_VERSION, 18)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            put(Telephony.Mms.MESSAGE_SIZE, parts.sumOf { it.data.size.toLong() })
            if (!subject.isNullOrBlank()) { put(Telephony.Mms.SUBJECT, subject); put(Telephony.Mms.SUBJECT_CHARSET, 106) }
            if (transactionId != null) put(Telephony.Mms.TRANSACTION_ID, transactionId)
        }
        val uri = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        for (part in parts) {
            if (part.contentType.contains("smil")) continue
            val text = part.contentType.startsWith("text/plain")
            val partValues = ContentValues().apply {
                put(Telephony.Mms.Part.MSG_ID, id)
                put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
                put(Telephony.Mms.Part.NAME, part.name)
                put(Telephony.Mms.Part.CONTENT_ID, "<${part.name}>")
                put(Telephony.Mms.Part.CONTENT_LOCATION, part.name)
                if (text) {
                    put(Telephony.Mms.Part.CHARSET, 106)
                    put(Telephony.Mms.Part.TEXT, String(part.data, Charsets.UTF_8))
                }
            }
            val partUri = context.contentResolver.insert(Uri.parse("content://mms/$id/part"), partValues) ?: continue
            if (!text) context.contentResolver.openOutputStream(partUri)?.use { it.write(part.data) }
        }
        return id
    }

    private fun addAddress(context: Context, mmsId: Long, address: String, type: Int) {
        val values = ContentValues().apply {
            put(Telephony.Mms.Addr.ADDRESS, address)
            put(Telephony.Mms.Addr.TYPE, type)
            put(Telephony.Mms.Addr.CHARSET, 106)
            put(Telephony.Mms.Addr.MSG_ID, mmsId)
        }
        context.contentResolver.insert(Uri.parse("content://mms/$mmsId/addr"), values)
    }
}
