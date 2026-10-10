package de.mm20.launcher2.ui.comms

import android.content.Context
import android.content.Intent
import android.telephony.PhoneNumberUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.comms.repository.SpamRepository
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.comms.privacy.CallGuard
import de.mm20.launcher2.comms.privacy.HiddenContacts
import de.mm20.launcher2.comms.privacy.PrivacySession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ContactsViewModel : ViewModel(), KoinComponent {

    private val contactDirectory: ContactDirectoryRepository by inject()
    private val commsSettings: CommsSettings by inject()
    private val spam: SpamRepository by inject()

    val contacts: StateFlow<List<DialerContact>> = combine(
        contactDirectory.observeContacts(),
        commsSettings.hiddenNumbers,
        commsSettings.hideFromContacts,
        PrivacySession.hiderUnlocked,
    ) { list, hidden, hide, unlocked ->
        if (!hide || unlocked) list
        else list.filter { !HiddenContacts.contactHidden(it, hidden) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clirPrefix = commsSettings.clirPrefix.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val clirEnabled = commsSettings.clirEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val tapToCall = commsSettings.tapToCall.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val confirmBeforeCall = commsSettings.confirmBeforeCall.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun dial(context: Context, phoneNumber: String) {
        if (phoneNumber.isEmpty()) return
        viewModelScope.launch { CallGuard.place(context, phoneNumber) }
    }

    /** Kept collected by the screen so that [numberFor] never has to block on the datastore */
    val defaultNumbers = commsSettings.contactDefaultNumbers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun numberFor(contact: DialerContact): String {
        if (contact.phoneNumbers.isEmpty()) return ""
        val mapped = defaultNumbers.value[contact.id.toString()].orEmpty()
        return contact.phoneNumbers.find { PhoneNumbers.match(it, mapped) } ?: contact.phoneNumbers.first()
    }

    fun share(context: Context, contact: DialerContact) {
        de.mm20.launcher2.ui.common.share.ShareActions.shareContactVcard(context, contact.displayName, contact.phoneNumbers, contact.emails)
    }

    fun block(number: String) {
        if (number.isEmpty()) return
        viewModelScope.launch { spam.setBlocked(number, true) }
    }
}
