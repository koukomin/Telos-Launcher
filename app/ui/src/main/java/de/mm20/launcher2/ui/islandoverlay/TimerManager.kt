package de.mm20.launcher2.ui.islandoverlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TimerState(val remainingMs: Long)

/**
 * A single in-process countdown timer for the Dynamic Island pill. Deliberately not persisted -
 * like every other Dynamic Island / Context Profile data source, it only exists while the
 * launcher process is alive.
 */
class TimerManager {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _state = MutableStateFlow<TimerState?>(null)
    val state: StateFlow<TimerState?> = _state.asStateFlow()

    private var tickJob: Job? = null

    fun start(durationMs: Long) {
        val endTime = System.currentTimeMillis() + durationMs
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    _state.value = null
                    break
                }
                _state.value = TimerState(remaining)
                delay(1000)
            }
        }
    }

    fun cancel() {
        tickJob?.cancel()
        _state.value = null
    }
}
