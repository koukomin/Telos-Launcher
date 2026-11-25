package de.mm20.launcher2.ktx

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Single Source of Truth for Audio Focus, Ducking, and Interruption Management across Telos Launcher
 * (Telos Music, Telos Radio, Telos Video, Napplets).
 */
object TelosAudioFocusManager {

    /**
     * Requests Audio Focus for playback with automatic handling for Android 8.0+ (API 26+) FocusRequest.
     */
    fun requestAudioFocus(
        context: Context,
        focusChangeChangeListener: AudioManager.OnAudioFocusChangeListener,
        usage: Int = AudioAttributes.USAGE_MEDIA,
        contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC
    ): AudioFocusRequest? {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeChangeListener)
                .build()

            val res = manager.requestAudioFocus(focusRequest)
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusRequest else null
        } else {
            @Suppress("DEPRECATION")
            val res = manager.requestAudioFocus(
                focusChangeChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) null else null
        }
    }

    /**
     * Abandons Audio Focus cleanly.
     */
    fun abandonAudioFocus(
        context: Context,
        focusRequest: AudioFocusRequest?,
        listener: AudioManager.OnAudioFocusChangeListener?
    ) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            manager.abandonAudioFocusRequest(focusRequest)
        } else if (listener != null) {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(listener)
        }
    }
}
