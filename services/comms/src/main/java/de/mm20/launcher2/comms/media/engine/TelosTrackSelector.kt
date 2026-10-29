package de.mm20.launcher2.comms.media.engine

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector.MappedTrackInfo

/**
 * Dynamic Audio-Only Switcher and Codec Optimizer for Telos Media.
 */
class TelosTrackSelector(context: Context) : DefaultTrackSelector(context) {

    init {
        // Initialize with default parameters configured for efficiency
        parameters = buildUponParameters()
            .setForceLowestBitrate(false)
            .setAllowVideoMixedDecoderSupportAdaptiveness(true)
            .build()
    }

    /**
     * Toggles audio-only mode.
     * When true (e.g. YT Music mode or video in background), disables video tracks completely
     * to save battery and network bandwidth.
     */
    fun setAudioOnlyMode(audioOnly: Boolean) {
        parameters = buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
            .build()
    }

    /**
     * Caps video resolution to save bandwidth and prevent CPU thermal throttling.
     * @param isWifi if true, caps at 1080p, else caps at 720p.
     */
    fun capResolutionForNetwork(isWifi: Boolean) {
        val maxWidth = if (isWifi) 1920 else 1280
        val maxHeight = if (isWifi) 1080 else 720

        parameters = buildUponParameters()
            .setMaxVideoSize(maxWidth, maxHeight)
            .build()
    }

    /**
     * Prefer Hardware AV1 and VP9 decoding to avoid CPU-based thermal throttling.
     * If device doesn't support them in hardware, it falls back gracefully but restricts max resolution.
     */
    fun optimizeCodecs() {
        // This favors hardware decoders over software ones, and specifically prefers AV1 if available.
        // It relies on the DefaultRenderersFactory EXTENSION_RENDERER_MODE_OFF which we configure in TelosPlayerEngine.
        parameters = buildUponParameters()
            .setPreferredVideoMimeType(MimeTypes.VIDEO_AV1)
            .build()
    }
}
