package de.mm20.launcher2.comms.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager

/**
 * "Reply with a message" when declining a call: the phone app hands over the number and the text.
 * A default SMS app has to offer this service.
 */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val number = intent.data?.schemeSpecificPart?.substringBefore('?')?.takeIf { it.isNotBlank() }
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (number != null && !text.isNullOrBlank()) SmsThreads.send(this, number, text)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
