package de.mm20.launcher2.data.comms

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.model.CallLogEntry
import de.mm20.launcher2.comms.model.CallType
import de.mm20.launcher2.comms.repository.CallLogRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class CallLogRepositoryImpl(
    private val context: Context,
    private val permissionsManager: PermissionsManager
) : CallLogRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRecents(): Flow<List<CallLogEntry>> {
        return permissionsManager.hasPermission(PermissionGroup.CallLog).flatMapLatest { granted ->
            if (!granted) {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Unit)
                        }
                    }
                    context.contentResolver.registerContentObserver(
                        CallLog.Calls.CONTENT_URI,
                        true,
                        observer
                    )
                    
                    // Initial trigger
                    trySend(Unit)
                    
                    awaitClose {
                        context.contentResolver.unregisterContentObserver(observer)
                    }
                }.map {
                    queryCallLog()
                }
            }
        }
    }

    private suspend fun queryCallLog(): List<CallLogEntry> = withContext(Dispatchers.IO) {
        try {
            val calls = mutableListOf<CallLogEntry>()
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_PHOTO_URI,
                CallLog.Calls.PHONE_ACCOUNT_ID,
                CallLog.Calls.CACHED_NUMBER_TYPE,
                CallLog.Calls.CACHED_NUMBER_LABEL,
            )

            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 100"
            )

            if (cursor == null) return@withContext emptyList()

            cursor.use { c ->
                val idCol = c.getColumnIndex(CallLog.Calls._ID)
                val numberCol = c.getColumnIndex(CallLog.Calls.NUMBER)
                val nameCol = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeCol = c.getColumnIndex(CallLog.Calls.TYPE)
                val dateCol = c.getColumnIndex(CallLog.Calls.DATE)
                val durationCol = c.getColumnIndex(CallLog.Calls.DURATION)
                val photoCol = c.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
                val simCol = c.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                val numTypeCol = c.getColumnIndex(CallLog.Calls.CACHED_NUMBER_TYPE)
                val numLabelCol = c.getColumnIndex(CallLog.Calls.CACHED_NUMBER_LABEL)
                
                if (idCol < 0 || numberCol < 0) return@withContext emptyList()

                while (c.moveToNext()) {
                    val typeInt = c.getInt(typeCol)
                    val callType = when (typeInt) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.Incoming
                        CallLog.Calls.OUTGOING_TYPE -> CallType.Outgoing
                        CallLog.Calls.MISSED_TYPE -> CallType.Missed
                        CallLog.Calls.REJECTED_TYPE -> CallType.Rejected
                        CallLog.Calls.BLOCKED_TYPE -> CallType.Blocked
                        else -> CallType.Unknown
                    }

                    calls.add(
                        CallLogEntry(
                            id = c.getLong(idCol),
                            phoneNumber = c.getString(numberCol) ?: "",
                            displayName = c.getString(nameCol),
                            type = callType,
                            timestamp = c.getLong(dateCol),
                            durationSeconds = c.getLong(durationCol),
                            photoUri = if (photoCol >= 0) c.getString(photoCol) else null,
                            simAccountId = if (simCol >= 0) c.getString(simCol)?.takeIf { it.isNotBlank() } else null,
                            numberLabel = if (numTypeCol >= 0 && c.getInt(numTypeCol) > 0) {
                                android.provider.ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                    context.resources,
                                    c.getInt(numTypeCol),
                                    if (numLabelCol >= 0) c.getString(numLabelCol) else null,
                                ).toString()
                            } else null,
                            simLabel = if (simCol >= 0) c.getString(simCol)?.takeLast(4)?.takeIf { it.isNotBlank() } else null,
                        )
                    )
                }
            }
            calls
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun observeForNumbers(numbers: List<String>): Flow<List<CallLogEntry>> {
        return observeRecents().map { recents ->
            recents.filter { entry -> numbers.any { PhoneNumbers.match(it, entry.phoneNumber) } }
        }
    }

    override suspend fun deleteForNumbers(numbers: List<String>) = withContext(Dispatchers.IO) {
        if (numbers.isEmpty()) return@withContext
        try {
            val recents = queryCallLog()
            val ids = recents
                .filter { entry -> numbers.any { PhoneNumbers.match(it, entry.phoneNumber) } }
                .map { it.id }
            for (id in ids) {
                context.contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID}=?",
                    arrayOf(id.toString()),
                )
            }
        } catch (_: SecurityException) {
        }
    }

    override suspend fun deleteById(id: Long) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID}=?",
                    arrayOf(id.toString()),
                )
            } catch (_: SecurityException) {
            }
        }
    }
}
