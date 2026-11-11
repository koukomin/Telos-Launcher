package de.mm20.launcher2.ui.component

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Low-latency Synthetic Audio Feedback Generator for Cyber UI interaction.
 * Plays ultra-lightweight synthetic blips and ticks with zero audio file assets.
 */
object TelosAudioFeedback {
    private var toneGen: ToneGenerator? = null

    init {
        runCatching {
            toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 15)
        }
    }

    fun playSciFiClick() {
        runCatching {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 20)
        }
    }

    fun playSciFiConfirm() {
        runCatching {
            toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 30)
        }
    }
}
