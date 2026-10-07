package de.mm20.launcher2.ui.screenrec

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The height of the recording. 0 is the height of the screen. */
enum class ScreenResolution(val key: String, val height: Int) {
    Native("native", 0),
    P1080("1080", 1080),
    P720("720", 720),
    P480("480", 480);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: P1080
    }
}

enum class ScreenQuality(val key: String, val bitRate: Int) {
    Low("low", 4_000_000),
    Medium("medium", 8_000_000),
    High("high", 16_000_000);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: Medium
    }
}

enum class ScreenAudio(val key: String) {
    None("none"),
    Microphone("mic");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: None
    }
}

/** The settings of Telos Screen Recorder, kept in the app's preferences */
class ScreenRecorderSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("telos_screen_recorder", Context.MODE_PRIVATE)

    var resolution: ScreenResolution
        get() = ScreenResolution.fromKey(prefs.getString("resolution", null))
        set(value) = prefs.edit().putString("resolution", value.key).apply()

    var fps: Int
        get() = prefs.getInt("fps", 30).takeIf { it == 30 || it == 60 } ?: 30
        set(value) = prefs.edit().putInt("fps", value).apply()

    var quality: ScreenQuality
        get() = ScreenQuality.fromKey(prefs.getString("quality", null))
        set(value) = prefs.edit().putString("quality", value.key).apply()

    var audio: ScreenAudio
        get() = ScreenAudio.fromKey(prefs.getString("audio", null))
        set(value) = prefs.edit().putString("audio", value.key).apply()

    /** Show a dot where the screen is touched while recording (needs the permission to change system settings) */
    var showTouches: Boolean
        get() = prefs.getBoolean("show_touches", false)
        set(value) = prefs.edit().putBoolean("show_touches", value).apply()

    /** Seconds to count down before the recording starts, 0 for none */
    var countdown: Int
        get() = prefs.getInt("countdown", 3).coerceIn(0, 5)
        set(value) = prefs.edit().putInt("countdown", value).apply()

    var stopOnScreenOff: Boolean
        get() = prefs.getBoolean("stop_on_screen_off", true)
        set(value) = prefs.edit().putBoolean("stop_on_screen_off", value).apply()
}

enum class ScreenRecStatus { Idle, Countdown, Recording, Paused }

data class ScreenRecState(
    val status: ScreenRecStatus = ScreenRecStatus.Idle,
    val elapsedMs: Long = 0,
    val countdown: Int = 0,
    val error: Boolean = false,
)

/** What the service tells the screens: the state of the recording */
object ScreenRecorderState {
    private val _state = MutableStateFlow(ScreenRecState())
    val state: StateFlow<ScreenRecState> = _state.asStateFlow()

    fun set(value: ScreenRecState) {
        _state.value = value
    }

    fun update(block: (ScreenRecState) -> ScreenRecState) {
        _state.value = block(_state.value)
    }
}
