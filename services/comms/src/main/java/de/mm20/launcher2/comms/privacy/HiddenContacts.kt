package de.mm20.launcher2.comms.privacy

import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.model.DialerContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PrivacySession {
    private val _hiderUnlocked = MutableStateFlow(false)
    val hiderUnlocked = _hiderUnlocked.asStateFlow()

    private val _phoneUnlocked = MutableStateFlow(false)
    val phoneUnlocked = _phoneUnlocked.asStateFlow()

    fun unlockHider() {
        _hiderUnlocked.value = true
    }

    fun lockHider() {
        _hiderUnlocked.value = false
    }

    fun unlockPhone() {
        _phoneUnlocked.value = true
    }
}

object HiddenContacts {
    fun matches(number: String, hidden: Map<String, String>): Boolean {
        if (number.isBlank() || hidden.isEmpty()) return false
        return hidden.keys.any { PhoneNumbers.match(it, number) }
    }

    fun contactHidden(contact: DialerContact, hidden: Map<String, String>): Boolean =
        contact.phoneNumbers.any { matches(it, hidden) }
}
