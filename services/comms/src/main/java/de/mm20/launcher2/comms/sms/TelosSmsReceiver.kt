// === TELOS_PENDING_REVIEW_START: sms_and_radio_engine ===
package de.mm20.launcher2.comms.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TelosSmsReceiver : BroadcastReceiver(), KoinComponent {

    // Using an optional inject in case VaultSmsRouter is not bound in some variants
    private val vaultRouter: VaultSmsRouter? by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
            intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) return

            val originatingAddress = messages[0].originatingAddress ?: return
            val fullBody = messages.joinToString("") { it.messageBody }

            Log.i("TelosSmsReceiver", "Received SMS from: $originatingAddress")

            val router = vaultRouter
            if (router != null) {
                // We use goAsync() to keep the receiver alive while coroutine evaluates vault status
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val isHidden = router.isHiddenContact(originatingAddress)
                        if (isHidden) {
                            Log.i("TelosSmsReceiver", "Intercepted SMS from hidden contact. Routing to Vault.")
                            // Type 1 = Inbox
                            router.saveSecretSms(originatingAddress, fullBody, System.currentTimeMillis(), 1)
                            
                            // If Telos is the Default SMS App, SMS_DELIVER is sent and we can abort
                            // the standard broadcast so other apps don't see it
                            if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
                                abortBroadcast()
                            }
                        } else {
                            // Let the system handle it normally (or write to standard Telephony DB if default SMS app)
                        }
                    } catch (e: Exception) {
                        Log.e("TelosSmsReceiver", "Error processing SMS", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: sms_and_radio_engine ===
