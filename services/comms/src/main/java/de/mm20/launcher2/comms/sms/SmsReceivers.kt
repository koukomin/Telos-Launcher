package de.mm20.launcher2.comms.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import de.mm20.launcher2.base.containedScope
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/** A text message arrives while Telos is the default SMS app: store it, then tell the user. */
class SmsDeliverReceiver : BroadcastReceiver(), KoinComponent {
    private val settings: CommsSettings by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return
        val address = messages[0].originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val date = messages[0].timestampMillis
        val pending = goAsync()
        containedScope(Dispatchers.IO).launch {
            try {
                SmsStore.insertInbox(context, address, body, date)
                SmsNotifier.incoming(context, settings, address, body)
            } finally {
                pending.finish()
            }
        }
    }
}

/** The result of sending a text message: the entry in the message store gets its final state. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val row = intent.data ?: return
        val type = if (resultCode == Activity.RESULT_OK) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED
        SmsStore.setType(context, row, type, keepFailed = type == Telephony.Sms.MESSAGE_TYPE_SENT)
    }
}

/** The announcement of a multimedia message: fetch it. */
class MmsPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (intent.type != "application/vnd.wap.mms-message") return
        val data = intent.getByteArrayExtra("data") ?: return
        val notification = MmsPdu.parseNotification(data)
        if (notification == null) {
            Log.w("MmsPush", "Not a message announcement")
            return
        }
        MmsTransport.download(context, notification, intent.getIntExtra("subscription", -1))
    }
}

/** A multimedia message was fetched: read the file, store the message, tell the user. */
class MmsDownloadedReceiver : BroadcastReceiver(), KoinComponent {
    private val settings: CommsSettings by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra("file") ?: return
        val file = File(path)
        if (resultCode != Activity.RESULT_OK) {
            Log.w("MmsDownload", "The download failed with $resultCode")
            file.delete()
            return
        }
        val tid = intent.getStringExtra("tid")
        val from = intent.getStringExtra("from")
        val pending = goAsync()
        containedScope(Dispatchers.IO).launch {
            try {
                val message = runCatching { MmsPdu.parseRetrieved(file.readBytes()) }.getOrNull()
                if (message != null) {
                    MmsStore.insertIncoming(context, message, tid, from)
                    val address = message.from ?: from ?: "MMS"
                    val text = message.parts.firstOrNull { it.contentType.startsWith("text/plain") }
                        ?.let { String(it.data, Charsets.UTF_8) }
                        ?: if (message.parts.any { it.contentType.startsWith("image/") }) SmsText.get(context, "au_messages_picture", "Picture")
                        else SmsText.get(context, "au_messages_mms", "Multimedia message")
                    SmsNotifier.incoming(context, settings, address, text)
                }
            } finally {
                file.delete()
                pending.finish()
            }
        }
    }
}

/** The result of sending a multimedia message */
class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("file")?.let { File(it).delete() }
        val row = intent.getStringExtra("row")?.let { Uri.parse(it) } ?: return
        val box = if (resultCode == Activity.RESULT_OK) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED
        MmsStore.setBox(context, row, box)
    }
}
