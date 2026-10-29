package de.mm20.launcher2.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncthingFolder(
    val id: String,
    val label: String,
    val path: String,
    val type: String = "Send & Receive",
    val status: String = "Up to date",
    val sizeFormatted: String = "1.2 GB",
    val fileCount: Int = 342,
    val isPaused: Boolean = false
)

data class SyncthingDevice(
    val id: String,
    val name: String,
    val address: String = "192.168.1.105:22000",
    val isConnected: Boolean = true,
    val downloadSpeed: String = "0 B/s",
    val uploadSpeed: String = "0 B/s",
    val isPaused: Boolean = false
)

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val _serviceRunning = MutableStateFlow(true)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _wifiOnly = MutableStateFlow(true)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _chargingOnly = MutableStateFlow(false)
    val chargingOnly: StateFlow<Boolean> = _chargingOnly.asStateFlow()

    private val _folders = MutableStateFlow(
        listOf(
            SyncthingFolder("default", "Default Folder", "/storage/emulated/0/Sync", "Send & Receive", "Up to date", "450 MB", 128),
            SyncthingFolder("camera-photos", "Camera Photos", "/storage/emulated/0/DCIM/Camera", "Send Only", "Up to date", "3.4 GB", 890),
            SyncthingFolder("documents", "Telos Documents", "/storage/emulated/0/Documents", "Send & Receive", "Up to date", "120 MB", 45)
        )
    )
    val folders: StateFlow<List<SyncthingFolder>> = _folders.asStateFlow()

    private val _devices = MutableStateFlow(
        listOf(
            SyncthingDevice("ABC1234-DEF5678-GHI9012", "Desktop Workstation", "192.168.1.10:22000", true, "1.2 MB/s", "45 KB/s"),
            SyncthingDevice("JKL3456-MNO7890-PQR1234", "Telos Laptop", "192.168.1.15:22000", true, "0 B/s", "0 B/s")
        )
    )
    val devices: StateFlow<List<SyncthingDevice>> = _devices.asStateFlow()

    fun toggleService() {
        _serviceRunning.value = !_serviceRunning.value
    }

    fun setWifiOnly(enabled: Boolean) {
        _wifiOnly.value = enabled
    }

    fun setChargingOnly(enabled: Boolean) {
        _chargingOnly.value = enabled
    }

    fun addFolder(label: String, path: String, type: String) {
        if (label.isBlank() || path.isBlank()) return
        val id = label.lowercase().replace(Regex("[^a-z0-9]"), "-")
        val newFolder = SyncthingFolder(id = id, label = label, path = path, type = type)
        _folders.value = _folders.value + newFolder
    }

    fun addDevice(id: String, name: String) {
        if (id.isBlank() || name.isBlank()) return
        val newDevice = SyncthingDevice(id = id, name = name, isConnected = false)
        _devices.value = _devices.value + newDevice
    }

    fun toggleFolderPause(folderId: String) {
        _folders.value = _folders.value.map {
            if (it.id == folderId) it.copy(isPaused = !it.isPaused, status = if (!it.isPaused) "Paused" else "Up to date") else it
        }
    }

    fun toggleDevicePause(deviceId: String) {
        _devices.value = _devices.value.map {
            if (it.id == deviceId) it.copy(isPaused = !it.isPaused) else it
        }
    }

    // --- SleepSync Features ---
    private val _sleepSyncEnabled = MutableStateFlow(false)
    val sleepSyncEnabled: StateFlow<Boolean> = _sleepSyncEnabled.asStateFlow()

    private val _sleepSyncMode = MutableStateFlow("Only on Wi-Fi")
    val sleepSyncMode: StateFlow<String> = _sleepSyncMode.asStateFlow()

    private val _dndDuringSync = MutableStateFlow(true)
    val dndDuringSync: StateFlow<Boolean> = _dndDuringSync.asStateFlow()

    private val _syncOnScreenOff = MutableStateFlow(false)
    val syncOnScreenOff: StateFlow<Boolean> = _syncOnScreenOff.asStateFlow()

    fun setSleepSyncEnabled(enabled: Boolean) {
        _sleepSyncEnabled.value = enabled
    }

    fun setSleepSyncMode(mode: String) {
        _sleepSyncMode.value = mode
    }

    fun setDndDuringSync(enabled: Boolean) {
        _dndDuringSync.value = enabled
    }

    fun setSyncOnScreenOff(enabled: Boolean) {
        _syncOnScreenOff.value = enabled
    }
}
