package de.mm20.launcher2.comms.repository

import de.mm20.launcher2.comms.model.MessageThread
import kotlinx.coroutines.flow.Flow

/**
 * Legacy conversation contract, retained for source compatibility but not bound in Koin.
 * The active Messages screen reads Android Telephony providers through SmsThreads/SmsStore,
 * including ContentObserver updates. No empty-data implementation is registered.
 */
interface MessageRepository {
    fun observeThreads(): Flow<List<MessageThread>>
}
