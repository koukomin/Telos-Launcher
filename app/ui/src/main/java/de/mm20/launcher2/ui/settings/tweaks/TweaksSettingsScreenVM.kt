package de.mm20.launcher2.ui.settings.tweaks

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class TweaksState(
    val isShizukuAvailable: Boolean = false,
    val isRootAvailable: Boolean = false,
    val clockSeconds: Boolean = false,
    val hideNavPill: Boolean = false,
    val peakRefresh120: Boolean = false,
    val volume30Steps: Boolean = false,
    val disableSafeVolume: Boolean = false,
    val forceAggressiveDoze: Boolean = false,
    val doubleTapSleep: Boolean = true,
    val callVibrateConnect: Boolean = false,
    val flashPulseCall: Boolean = false,
    val pocketMode: Boolean = true,
    val caffeinate: Boolean = false,
    val mobileDataAlwaysOn: Boolean = false,
    val alwaysOnDisplay: Boolean = false,
)

class TweaksSettingsScreenVM(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TweaksState())
    val state: StateFlow<TweaksState> = _state.asStateFlow()

    suspend fun refreshBackendState() {
        withContext(Dispatchers.IO) {
            val shizuku = try {
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) { false }

            val root = runCatching { collectProcess(ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start())?.contains("uid=0") == true }.getOrDefault(false)

            _state.value = _state.value.copy(
                isShizukuAvailable = shizuku,
                isRootAvailable = root,
            )
            val snapshot = _state.value
            _state.value = snapshot.copy(
                clockSeconds = execute("settings get secure clock_seconds")?.trim() == "1",
                mobileDataAlwaysOn = execute("settings get global mobile_data_always_on")?.trim() == "1",
                alwaysOnDisplay = execute("settings get secure doze_always_on")?.trim() == "1",
                callVibrateConnect = execute("settings get system vibrate_on_call_connected")?.trim() == "1",
                peakRefresh120 = execute("settings get system peak_refresh_rate")?.trim()?.toFloatOrNull()?.let { it >= 120f } == true,
            )
        }
    }

    fun toggleClockSeconds(enabled: Boolean) {
        val cmd = "settings put secure clock_seconds ${if (enabled) 1 else 0}"
        runCommand(cmd) { _state.value = _state.value.copy(clockSeconds = enabled) }
    }

    fun toggleHideNavPill(enabled: Boolean) {
        val cmd = if (enabled) {
            "cmd overlay enable com.android.internal.systemui.navbar.gestural"
        } else {
            "cmd overlay disable com.android.internal.systemui.navbar.gestural"
        }
        runCommand(cmd) { _state.value = _state.value.copy(hideNavPill = enabled) }
    }

    fun togglePeakRefresh120(enabled: Boolean) {
        val cmd = "settings put system peak_refresh_rate ${if (enabled) 120.0 else 60.0}"
        runCommand(cmd) { _state.value = _state.value.copy(peakRefresh120 = enabled) }
    }

    fun toggleVolume30Steps(enabled: Boolean) {
        val cmd = "settings put system volume_music_steps ${if (enabled) 30 else 15}"
        runCommand(cmd) { _state.value = _state.value.copy(volume30Steps = enabled) }
    }

    fun toggleDisableSafeVolume(enabled: Boolean) {
        val cmd = "settings put global audio_safe_volume_state ${if (enabled) 2 else 1}"
        runCommand(cmd) { _state.value = _state.value.copy(disableSafeVolume = enabled) }
    }

    fun toggleForceDoze(enabled: Boolean) {
        val cmd = if (enabled) "dumpsys deviceidle force-idle" else "dumpsys deviceidle unforce"
        runCommand(cmd) { _state.value = _state.value.copy(forceAggressiveDoze = enabled) }
    }

    fun toggleDoubleTapSleep(enabled: Boolean) {
        _state.value = _state.value.copy(doubleTapSleep = enabled)
    }

    fun toggleCallVibrateConnect(enabled: Boolean) {
        val cmd = "settings put system vibrate_on_call_connected ${if (enabled) 1 else 0}"
        runCommand(cmd) { _state.value = _state.value.copy(callVibrateConnect = enabled) }
    }

    fun toggleFlashPulseCall(enabled: Boolean) {
        _state.value = _state.value.copy(flashPulseCall = enabled)
    }

    fun togglePocketMode(enabled: Boolean) {
        _state.value = _state.value.copy(pocketMode = enabled)
    }

    fun toggleCaffeinate(enabled: Boolean) {
        _state.value = _state.value.copy(caffeinate = enabled)
    }

    fun toggleMobileDataAlwaysOn(enabled: Boolean) {
        val cmd = "settings put global mobile_data_always_on ${if (enabled) 1 else 0}"
        runCommand(cmd) { _state.value = _state.value.copy(mobileDataAlwaysOn = enabled) }
    }

    fun toggleAlwaysOnDisplay(enabled: Boolean) {
        val cmd = "settings put secure doze_always_on ${if (enabled) 1 else 0}"
        runCommand(cmd) { _state.value = _state.value.copy(alwaysOnDisplay = enabled) }
    }

    private val commands = kotlinx.coroutines.sync.Mutex()
    private fun runCommand(cmd: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            commands.lock()
            try {
                val success = withContext(Dispatchers.IO) { execute(cmd) != null }
                if (success) onSuccess()
            } finally { commands.unlock() }
        }
    }
    private fun execute(cmd: String): String? = runCatching {
        val backend = _state.value
        val process = when {
            backend.isRootAvailable -> ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            backend.isShizukuAvailable -> {
                val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                method.isAccessible = true
                method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process
            }
            else -> return@runCatching null
        }
        collectProcess(process)
    }.getOrNull()
    private fun collectProcess(process: java.lang.Process): String? = try {
        process.outputStream.close()
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) null
        else if (process.exitValue() != 0) null
        else process.inputStream.bufferedReader().use { it.readText().take(4096) }
    } finally {
        process.inputStream.close(); process.errorStream.close(); process.destroy()
    }
}
