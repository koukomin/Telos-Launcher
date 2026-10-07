package de.mm20.launcher2.comms.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import de.mm20.launcher2.comms.intent.MessengerIntentUtils
import de.mm20.launcher2.ktx.tryStartActivity

object QuickSms {
    /** True when the message was handed to the phone's SMS service */
    fun send(context: Context, number: String, body: String): Boolean {
        if (number.isBlank() || body.isBlank()) return false
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            return runCatching {
                @Suppress("DEPRECATION")
                val sms = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
                sms.sendTextMessage(number, null, body, null, null)
            }.isSuccess
        }
        context.tryStartActivity(MessengerIntentUtils.sms(number, body))
        return false
    }
}
