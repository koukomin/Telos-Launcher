// === TELOS_PENDING_REVIEW_START: telephony_encryption_suite ===
package de.mm20.launcher2.comms.telephony

import android.net.Uri
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import de.mm20.launcher2.comms.repository.SpamRepository

class TelosCallScreeningService : CallScreeningService(), KoinComponent {

    private val commsSettings: CommsSettings by inject()
    private val spamRepository: SpamRepository by inject()
    private val scope = CoroutineScope(Job() + Dispatchers.IO)


    override fun onScreenCall(callDetails: Call.Details) {
        val handle: Uri? = callDetails.handle
        val phoneNumber = handle?.schemeSpecificPart ?: ""
        
        Log.i("TelosCallScreening", "Screening call from: $phoneNumber")

        scope.launch {
            val isBlacklisted = isNumberBlacklisted(phoneNumber)
            if (isBlacklisted) {
                Log.i("TelosCallScreening", "Silently rejecting call from $phoneNumber")
                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(true)
                    .setSkipNotification(true)
                    .build()
                respondToCall(callDetails, response)
            } else {
                Log.i("TelosCallScreening", "Allowing call from $phoneNumber")
                val response = CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
                respondToCall(callDetails, response)
            }
        }
    }

    private suspend fun isNumberBlacklisted(number: String): Boolean {
        if (!commsSettings.enableSpamBlocking.first()) return false
        return spamRepository.isNumberBlocked(number)
    }
}
// === TELOS_PENDING_REVIEW_END: telephony_encryption_suite ===
