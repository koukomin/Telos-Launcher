package de.mm20.launcher2.comms.media

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sleep timer for the music player. The UI starts it, the player service registers [onExpire] and stops
 * playback when time is up. Lives in the app process like the service itself.
 */
object MusicSleepTimer {
    private val handler = Handler(Looper.getMainLooper())
    private val _endsAt = MutableStateFlow(0L)

    /** Wall-clock time (ms) when playback will stop, or 0 when no timer is running */
    val endsAt: StateFlow<Long> = _endsAt

    var onExpire: (() -> Unit)? = null

    private val expire = Runnable {
        _endsAt.value = 0L
        onExpire?.invoke()
    }

    fun start(minutes: Int) {
        handler.removeCallbacks(expire)
        if (minutes <= 0) {
            _endsAt.value = 0L
            return
        }
        val millis = minutes * 60_000L
        _endsAt.value = System.currentTimeMillis() + millis
        handler.postDelayed(expire, millis)
    }

    fun cancel() = start(0)
}
