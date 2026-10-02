package de.mm20.launcher2.ui.comms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Backs [ContactsScreen]: the full contact directory. */
class ContactsViewModel : ViewModel(), KoinComponent {

    private val contactDirectory: ContactDirectoryRepository by inject()

    val contacts: StateFlow<List<DialerContact>> = contactDirectory.observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
