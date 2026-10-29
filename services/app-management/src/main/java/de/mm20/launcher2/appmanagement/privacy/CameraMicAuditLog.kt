package de.mm20.launcher2.appmanagement.privacy

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 24-Hour Camera & Microphone Access Privacy Audit Log for Telos Launcher.
 * Records app package, access type (camera/mic/location), timestamp, and duration.
 */
object CameraMicAuditLog {
    private const val TAG = "CameraMicAuditLog"

    enum class AccessType { Camera, Microphone, Location }

    data class AuditEntry(
        val packageName: String,
        val type: AccessType,
        val timestamp: Long = System.currentTimeMillis(),
        val durationMs: Long = 0L
    )

    private val _logs = MutableStateFlow<List<AuditEntry>>(emptyList())
    val logs: StateFlow<List<AuditEntry>> = _logs.asStateFlow()

    fun recordAccess(packageName: String, type: AccessType, durationMs: Long = 0L) {
        if (packageName.isBlank()) return
        val entry = AuditEntry(packageName = packageName, type = type, durationMs = durationMs)
        val cutoff = System.currentTimeMillis() - 86_400_000L // 24 hours
        _logs.value = (_logs.value.filter { it.timestamp > cutoff } + entry).takeLast(100)
        Log.i(TAG, "Recorded $type access for $packageName")
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
