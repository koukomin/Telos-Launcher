// === TELOS_PENDING_REVIEW_START: telephony_encryption_suite ===
package de.mm20.launcher2.comms.telephony

import de.mm20.launcher2.base.containedScope
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils
import android.util.Log
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.comms.repository.SpamRepository
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TelosCallScreeningService : CallScreeningService(), KoinComponent {

    private val commsSettings: CommsSettings by inject()
    private val spamRepository: SpamRepository by inject()
    private val contacts: ContactDirectoryRepository by inject()
    private val scope = containedScope(Dispatchers.IO)


    override fun onScreenCall(callDetails: Call.Details) {
        // only incoming calls are screened: outgoing calls (also emergency calls) always go through
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }
        val handle: Uri? = callDetails.handle
        val phoneNumber = handle?.schemeSpecificPart ?: ""
        
        val presentation = callDetails.handlePresentation
        scope.launch {
            // a failing lookup must never leave the call without an answer
            val block = runCatching { shouldBlock(phoneNumber, presentation) }.getOrDefault(false)
            if (block) {
                Log.i("TelosCallScreening", "Silently rejecting a call")
                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(true)
                    .setSkipNotification(true)
                    .build()
                respondToCall(callDetails, response)
            } else {
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

    private suspend fun shouldBlock(number: String, presentation: Int): Boolean {
        val hidden = number.isBlank() ||
            presentation == android.telecom.TelecomManager.PRESENTATION_RESTRICTED ||
            presentation == android.telecom.TelecomManager.PRESENTATION_UNKNOWN
        if (hidden && commsSettings.blockHiddenNumbers.first()) return true

        if (commsSettings.enableSpamBlocking.first() &&
            number.isNotBlank() &&
            spamRepository.isNumberBlocked(number)
        ) return true

        if (commsSettings.blockUnknownNumbers.first() &&
            number.isNotBlank() &&
            !contacts.containsNumber(number)
        ) return true

        if (commsSettings.blockInternational.first() && isInternational(number)) return true

        return false
    }

    private suspend fun isInternational(number: String): Boolean {
        if (number.none { it.isDigit() }) return false
        if (PhoneNumberUtils.isEmergencyNumber(number)) return false
        val home = HomeCountry.resolve(this, commsSettings.homeCountry.first())
        return HomeCountry.isInternational(number, home)
    }
}
// === TELOS_PENDING_REVIEW_END: telephony_encryption_suite ===
