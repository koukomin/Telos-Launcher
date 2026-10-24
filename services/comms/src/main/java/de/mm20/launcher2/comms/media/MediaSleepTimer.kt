package de.mm20.launcher2.comms.media

import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MediaSleepTimer {
    private var timerJob: Job? = null
    var isRunning: Boolean = false
        private set
    var remainingSeconds: Int = 0
        private set

    fun startTimer(scope: CoroutineScope, durationMinutes: Int, controllerProvider: () -> MediaController?) {
        cancel()
        if (durationMinutes <= 0) return
        isRunning = true
        remainingSeconds = durationMinutes * 60

        timerJob = scope.launch(Dispatchers.Default) {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--

                val controller = controllerProvider()
                // Smooth 30s volume fade out before time expires
                if (remainingSeconds in 1..30 && controller != null) {
                    val volumeRatio = remainingSeconds / 30f
                    controller.volume = volumeRatio
                }
            }

            val controller = controllerProvider()
            controller?.pause()
            controller?.volume = 1.0f
            isRunning = false
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        isRunning = false
        remainingSeconds = 0
    }
}
