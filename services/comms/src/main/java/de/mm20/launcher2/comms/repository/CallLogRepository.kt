package de.mm20.launcher2.comms.repository

import de.mm20.launcher2.comms.model.CallLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * The device's call log, for the Recents tab.
 *
 * Phase 1 ships only the interface plus a stub implementation that always emits an empty list -
 * reading `CallLog.Calls` (new `READ_CALL_LOG` permission group, `ContentObserver`-based live
 * updates) is Phase 2 scope, alongside the Secret Vault/SQLCipher/Call Recording work. The
 * Recents screen is real, navigable UI today; it just has no data behind it yet.
 */
interface CallLogRepository {
    fun observeRecents(): Flow<List<CallLogEntry>>
    fun observeForNumbers(numbers: List<String>): Flow<List<CallLogEntry>>
    suspend fun deleteForNumbers(numbers: List<String>)
    suspend fun deleteById(id: Long)
}
