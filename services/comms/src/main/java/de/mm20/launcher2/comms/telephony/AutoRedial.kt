package de.mm20.launcher2.comms.telephony

import de.mm20.launcher2.base.containedScope
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.DisconnectCause
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object AutoRedial : KoinComponent {
    private val commsSettings: CommsSettings by inject()
    private val scope = containedScope(Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var remaining = 0
    private var lastNumber = ""
    private var redialing = false

    /** An incoming call is not a call to redial, forget the last outgoing one */
    fun onIncoming() {
        remaining = 0
        lastNumber = ""
        redialing = false
        handler.removeCallbacksAndMessages(null)
    }

    fun onOutgoing(number: String) {
        lastNumber = number
        if (redialing) {
            // this is the redial itself: keep counting down instead of starting over
            redialing = false
            return
        }
        handler.removeCallbacksAndMessages(null)
        remaining = 0
        scope.launch {
            if (commsSettings.autoRedial.first()) {
                remaining = commsSettings.autoRedialAttempts.first()
            }
        }
    }

    fun onConnected() {
        remaining = 0
        handler.removeCallbacksAndMessages(null)
    }

    fun onDisconnected(context: Context, call: Call) {
        val cause = call.details?.disconnectCause?.code ?: return
        if (cause == DisconnectCause.LOCAL) remaining = 0 // the user hung up
        val retryable = cause == DisconnectCause.BUSY ||
            cause == DisconnectCause.MISSED ||
            cause == DisconnectCause.REJECTED
        if (!retryable || remaining <= 0 || lastNumber.isEmpty()) return
        remaining -= 1
        scope.launch {
            val delayMs = commsSettings.autoRedialDelaySec.first().coerceIn(3, 60) * 1000L
            handler.postDelayed({
                redialing = true
                SimRouter.place(context, lastNumber)
            }, delayMs)
        }
    }
}
