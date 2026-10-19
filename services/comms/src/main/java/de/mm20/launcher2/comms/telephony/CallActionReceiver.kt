package de.mm20.launcher2.comms.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Explicit, immutable notification actions; an old notification cannot control a newer call. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("call_action")
        if (!callActionAllowed(TelosCallSession.ui.value, intent.getLongExtra("call_token", 0), action)) return
        when (action) {
            "answer" -> TelosCallSession.answer()
            "reject" -> TelosCallSession.reject()
            "hangup" -> TelosCallSession.hangup()
        }
    }
}
