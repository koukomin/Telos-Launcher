package de.mm20.launcher2.ui.backup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import de.mm20.launcher2.backup.BackupGroup

data class AppBackupEntry(
    val packageName: String,
    val label: String,
    val versionName: String,
    val hasApkBackup: Boolean,
    val hasDataBackup: Boolean,
    val backupDateMs: Long? = null,
    val backupSizeBytes: Long = 0L,
    val isSystemApp: Boolean = false
)

data class BackupJobProgress(
    val inProgress: Boolean = false,
    val currentTask: String = "",
    val progress: Float = 0f,
    val totalItems: Int = 0,
    val completedItems: Int = 0,
    val error: String? = null
)

class TelosBackupViewModel(application: Application) : AndroidViewModel(application) {

    private val _appList = MutableStateFlow<List<AppBackupEntry>>(emptyList())
    val appList: StateFlow<List<AppBackupEntry>> = _appList.asStateFlow()

    private val _systemDataList = MutableStateFlow<List<String>>(
        listOf("SMS Messages", "Call Logs", "Wi-Fi Networks", "Bluetooth Pairings")
    )
    val systemDataList: StateFlow<List<String>> = _systemDataList.asStateFlow()

    private val _jobProgress = MutableStateFlow(BackupJobProgress())
    val jobProgress: StateFlow<BackupJobProgress> = _jobProgress.asStateFlow()

    private val _shizukuAvailable = MutableStateFlow(false)
    val shizukuAvailable: StateFlow<Boolean> = _shizukuAvailable.asStateFlow()

    init {
        // Initialize with dummy/placeholder data for now until the backup engine is fully wired
        _appList.value = listOf(
            AppBackupEntry("com.whatsapp", "WhatsApp", "2.23.4.5", true, true, System.currentTimeMillis() - 86400000L, 1024L * 1024 * 54),
            AppBackupEntry("org.mozilla.firefox", "Firefox", "120.0.1", true, false, System.currentTimeMillis() - 172800000L, 1024L * 1024 * 120),
            AppBackupEntry("com.google.android.youtube", "YouTube", "18.45.33", false, false, isSystemApp = true)
        )
    }

    fun startBackup(apps: List<AppBackupEntry>, includeApks: Boolean, includeData: Boolean) {
        // Placeholder for engine launch
    }

    fun startRestore(apps: List<AppBackupEntry>, restoreApks: Boolean, restoreData: Boolean) {
        // Placeholder for engine launch
    }

    fun checkShizuku() {
        // Placeholder for checking Shizuku/Root
        _shizukuAvailable.value = true
    }
}
