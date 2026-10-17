package de.mm20.launcher2.comms.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import de.mm20.launcher2.base.containedScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
class SmsNotifActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra("address").orEmpty()
        when (intent.action) {
            REPLY -> {
                val text = android.app.RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence("reply")?.toString().orEmpty()
                if (address.isNotBlank() && text.isNotBlank()) {
                    val pending = goAsync()
                    containedScope(Dispatchers.IO).launch {
                        try { SmsThreads.send(context, address, text) } finally { pending.finish() }
                    }
                    context.getSystemService(NotificationManager::class.java)?.cancel(address.hashCode())
                }
            }
            READ -> {
                val pending = goAsync()
                containedScope(Dispatchers.IO).launch {
                    try {
                        val thread = SmsThreads.conversations(context).firstOrNull {
                            it.address.split(", ").any { n -> n.filter { c -> c.isDigit() } == address.filter { c -> c.isDigit() } }
                        }
                        if (thread != null) SmsStore.markThreadRead(context, thread.threadId)
                    } finally { pending.finish() }
                }
                context.getSystemService(NotificationManager::class.java)?.cancel(address.hashCode())
            }
            SPEAK -> SmsTts.speak(context, intent.getStringExtra("text").orEmpty())
        }
    }

    companion object {
        const val REPLY = "de.mm20.launcher2.SMS_NOTIF_REPLY"
        const val READ = "de.mm20.launcher2.SMS_NOTIF_READ"
        const val SPEAK = "de.mm20.launcher2.SMS_NOTIF_SPEAK"
    }
}
