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

    private val _recordingsUnlocked = MutableStateFlow(false)
    val recordingsUnlocked = _recordingsUnlocked.asStateFlow()

    private val _filesUnlocked = MutableStateFlow(false)
    val filesUnlocked = _filesUnlocked.asStateFlow()

    fun unlockHider() {
        _hiderUnlocked.value = true
    }

    fun lockHider() {
        _hiderUnlocked.value = false
    }

    fun unlockPhone() {
        _phoneUnlocked.value = true
    }

    fun unlockRecordings() {
        _recordingsUnlocked.value = true
    }

    fun lockRecordings() {
        _recordingsUnlocked.value = false
    }

    fun unlockFiles() {
        _filesUnlocked.value = true
    }
}

object HiddenContacts {
    fun matches(number: String, hidden: Map<String, String>): Boolean {
        if (number.isBlank() || hidden.isEmpty()) return false
        return hidden.keys.any { de.mm20.launcher2.ktx.PhoneNumberKey.matches(it, number) }
    }

    /** Also protect the other numbers of contacts hidden by older releases (which saved only one). */
    fun expanded(hidden: Map<String, String>, contacts: List<DialerContact>): Map<String, String> {
        if (hidden.isEmpty()) return hidden
        val result = hidden.toMutableMap()
        contacts.filter { contactHidden(it, hidden) }.forEach { contact ->
            contact.phoneNumbers.forEach { number ->
                de.mm20.launcher2.ktx.PhoneNumberKey.of(number).takeIf(String::isNotEmpty)?.let { result[it] = "1" }
            }
        }
        return result
    }

    fun contactHidden(contact: DialerContact, hidden: Map<String, String>): Boolean =
        contact.phoneNumbers.any { matches(it, hidden) }
}
