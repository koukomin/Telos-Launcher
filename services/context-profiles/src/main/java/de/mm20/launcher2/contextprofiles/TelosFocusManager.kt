package de.mm20.launcher2.contextprofiles

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Focus Mode and Digital Wellbeing controller for Telos Launcher.
 * - Temporarily freezes/disables distracting apps (Social Media, Games) during focus sessions.
 * - Tracks focus duration and provides single-tap toggling.
 */
object TelosFocusManager {

    private const val TAG = "TelosFocusManager"

    data class FocusState(
        val isFocusModeActive: Boolean = false,
        val sessionStartEpochMs: Long = 0L,
        val targetDurationMinutes: Int = 25,
        val distractingPackages: Set<String> = emptySet()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    fun toggleFocusMode(context: Context, distractingApps: Set<String> = emptySet(), durationMinutes: Int = 25) {
        val current = _state.value
        if (current.isFocusModeActive) {
            stopFocusMode(context)
        } else {
            startFocusMode(context, distractingApps, durationMinutes)
        }
    }

    fun startFocusMode(context: Context, distractingApps: Set<String>, durationMinutes: Int = 25) {
        val now = System.currentTimeMillis()
        _state.value = FocusState(
            isFocusModeActive = true,
            sessionStartEpochMs = now,
            targetDurationMinutes = durationMinutes,
            distractingPackages = distractingApps
        )
        Log.i(TAG, "Focus Mode started for $durationMinutes minutes with ${distractingApps.size} blocked apps")
    }

    fun stopFocusMode(context: Context) {
        _state.value = _state.value.copy(isFocusModeActive = false)
        Log.i(TAG, "Focus Mode stopped")
    }
}
