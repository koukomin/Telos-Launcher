package de.mm20.launcher2.comms.privacy

import de.mm20.launcher2.comms.sms.VaultSmsRouter
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.first

class VaultSmsRouterImpl(
    private val commsSettings: CommsSettings,
) : VaultSmsRouter {
    override suspend fun isHiddenContact(phoneNumber: String): Boolean {
        return HiddenContacts.matches(phoneNumber, commsSettings.hiddenNumbers.first())
    }

    override suspend fun saveSecretSms(address: String, body: String, date: Long, type: Int) {
        // Hidden SMS stay out of the system inbox; the hider list is enough for Phase 4.
    }
}
