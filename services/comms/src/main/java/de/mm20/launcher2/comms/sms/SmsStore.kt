package de.mm20.launcher2.comms.sms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony

/**
 * Writes to the system's message store. Only the default SMS app may do that, so every function
 * does nothing (and says so) when Telos is not the default.
 */
object SmsStore {

    fun insertInbox(context: Context, address: String, body: String, date: Long): Uri? {
        if (!SmsRole.isDefault(context)) return null
        return runCatching {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.DATE_SENT, date)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        }.getOrNull()
    }

    /** A message that is about to be sent, in the outbox until the result is known */
    fun insertOutgoing(context: Context, address: String, body: String, subId: Int = -1): Uri? {
        if (!SmsRole.isDefault(context)) return null
        return runCatching {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
            }
            context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
        }.getOrNull()
    }

    /** [keepFailed]: a message of several parts reports once per part; one failed part keeps the whole message failed */
    fun setType(context: Context, uri: Uri, type: Int, keepFailed: Boolean = false) {
        runCatching {
            val values = ContentValues().apply { put(Telephony.Sms.TYPE, type) }
            if (keepFailed) {
                context.contentResolver.update(uri, values, "${Telephony.Sms.TYPE} != ?", arrayOf(Telephony.Sms.MESSAGE_TYPE_FAILED.toString()))
            } else {
                context.contentResolver.update(uri, values, null, null)
            }
        }
    }

    /** What the user opened is read now */
    fun markThreadRead(context: Context, threadId: Long) {
        if (threadId < 0 || !SmsRole.isDefault(context)) return
        val values = ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) }
        runCatching {
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
        }
        runCatching {
            context.contentResolver.update(Telephony.Mms.CONTENT_URI, values, "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0", arrayOf(threadId.toString()))
        }
    }
}
