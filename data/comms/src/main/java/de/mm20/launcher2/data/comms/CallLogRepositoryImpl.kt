package de.mm20.launcher2.data.comms

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
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
        val calls = mutableListOf<CallLogEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 100"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(CallLog.Calls._ID)
                val numberCol = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameCol = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeCol = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateCol = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationCol = cursor.getColumnIndex(CallLog.Calls.DURATION)
                
                if (idCol < 0 || numberCol < 0) return@withContext emptyList()

                while (cursor.moveToNext()) {
                    val typeInt = cursor.getInt(typeCol)
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
                            id = cursor.getLong(idCol),
                            phoneNumber = cursor.getString(numberCol) ?: "",
                            displayName = cursor.getString(nameCol),
                            type = callType,
                            timestamp = cursor.getLong(dateCol),
                            durationSeconds = cursor.getLong(durationCol)
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission might have been revoked mid-query
        }
        
        calls
    }
}
