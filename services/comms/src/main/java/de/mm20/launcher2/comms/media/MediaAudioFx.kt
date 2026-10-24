package de.mm20.launcher2.comms.media

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log

object MediaAudioFx {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = -1

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId == -1 || audioSessionId == currentSessionId) return
        release()
        currentSessionId = audioSessionId
        runCatching {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
            virtualizer = Virtualizer(0, audioSessionId).apply { enabled = true }
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply { enabled = true }
        }.onFailure {
            Log.w("MediaAudioFx", "Failed to initialize AudioFx: ${it.message}")
        }
    }

    fun setBassBoostStrength(strength: Int) { // 0 - 1000
        runCatching {
            bassBoost?.setStrength(strength.toShort().coerceIn(0, 1000))
        }
    }

    fun setVirtualizerStrength(strength: Int) { // 0 - 1000 (Spatial Audio 3D)
        runCatching {
            virtualizer?.setStrength(strength.toShort().coerceIn(0, 1000))
        }
    }

    fun setLoudnessGain(gainMb: Int) { // 0 - 2000 (0 to +20dB)
        runCatching {
            loudnessEnhancer?.setTargetGain(gainMb.coerceIn(0, 2000))
        }
    }

    fun setBandLevel(band: Short, levelMb: Short) {
        runCatching {
            equalizer?.setBandLevel(band, levelMb)
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        currentSessionId = -1
    }
}
