// === TELOS_PENDING_REVIEW_START: sms_and_radio_engine ===
package de.mm20.launcher2.comms.sms

import android.content.Context
import android.telephony.SmsManager
import android.util.Log

class SmsRepository(private val context: Context) {

    fun sendSms(destinationAddress: String, text: String): Boolean {
        try {
            val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
                ?: return false

            // We divide the message if it's too long
            val parts = smsManager.divideMessage(text)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(destinationAddress, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(destinationAddress, null, text, null, null)
            }
            return true
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to send SMS to $destinationAddress", e)
            return false
        }
    }
}
// === TELOS_PENDING_REVIEW_END: sms_and_radio_engine ===
