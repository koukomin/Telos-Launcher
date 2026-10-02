package de.mm20.launcher2.comms.repository

import de.mm20.launcher2.comms.model.DialerContact
import kotlinx.coroutines.flow.Flow

/** The device's contact directory, for the dialer/T9 search and the Contacts tab. */
interface ContactDirectoryRepository {
    /** Empty (not an error) while READ_CONTACTS isn't granted. */
    fun observeContacts(): Flow<List<DialerContact>>
}
