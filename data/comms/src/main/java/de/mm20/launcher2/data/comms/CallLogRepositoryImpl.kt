package de.mm20.launcher2.data.comms

import de.mm20.launcher2.comms.model.CallLogEntry
import de.mm20.launcher2.comms.repository.CallLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Phase 1 stub - see [CallLogRepository]'s doc comment. Replaced with a real `CallLog.Calls`-backed implementation in Phase 2. */
internal class CallLogRepositoryImpl : CallLogRepository {
    override fun observeRecents(): Flow<List<CallLogEntry>> = flowOf(emptyList())
}
