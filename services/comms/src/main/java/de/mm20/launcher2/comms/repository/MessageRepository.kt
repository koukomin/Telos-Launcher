package de.mm20.launcher2.comms.repository

import de.mm20.launcher2.comms.model.MessageThread
import kotlinx.coroutines.flow.Flow

/**
 * The device's SMS/MMS conversation threads, for the Messages tab.
 *
 * Phase 1 ships only the interface plus a stub implementation that always emits an empty list -
 * reading `Telephony.Sms`/`Telephony.Mms` (requires being the default SMS app or holding
 * `READ_SMS`, a new permission group, plus `ContentObserver`-based live updates) is Phase 2 scope.
 * The Messages screen is real, navigable UI today; it just has no data behind it yet.
 */
interface MessageRepository {
    fun observeThreads(): Flow<List<MessageThread>>
}
