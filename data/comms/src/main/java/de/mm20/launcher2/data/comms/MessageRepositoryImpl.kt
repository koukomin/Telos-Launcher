package de.mm20.launcher2.data.comms

import de.mm20.launcher2.comms.model.MessageThread
import de.mm20.launcher2.comms.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Phase 1 stub - see [MessageRepository]'s doc comment. Replaced with a real `Telephony.Sms`-backed implementation in Phase 2. */
internal class MessageRepositoryImpl : MessageRepository {
    override fun observeThreads(): Flow<List<MessageThread>> = flowOf(emptyList())
}
