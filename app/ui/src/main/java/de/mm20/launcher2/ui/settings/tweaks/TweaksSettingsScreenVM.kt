package de.mm20.launcher2.ui.settings.tweaks

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
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

            val root = try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor() == 0 && out.contains("uid=0")
            } catch (_: Exception) { false }

            _state.value = _state.value.copy(
                isShizukuAvailable = shizuku,
                isRootAvailable = root,
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

    private fun runCommand(cmd: String, onSuccess: () -> Unit) {
        val currentState = _state.value
        if (!currentState.isShizukuAvailable && !currentState.isRootAvailable) return
        Thread {
            try {
                val args = if (currentState.isRootAvailable) arrayOf("su", "-c", cmd) else arrayOf("sh", "-c", cmd)
                val process = Runtime.getRuntime().exec(args)
                if (process.waitFor() == 0) {
                    onSuccess()
                }
            } catch (_: Exception) {}
        }.start()
    }
}
