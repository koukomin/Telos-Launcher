package de.mm20.launcher2.comms.media

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer

/** Shared Telos effect settings, separate native resources for each live player session. */
object MediaAudioFx {
    private data class Effects(val eq: Equalizer?, val bass: BassBoost?, val spatial: Virtualizer?, val loudness: LoudnessEnhancer?) {
        fun close() { runCatching { eq?.release() }; runCatching { bass?.release() }; runCatching { spatial?.release() }; runCatching { loudness?.release() } }
    }
    private val sessions = mutableMapOf<Int, Effects>()
    private var bass = 0
    private var spatial = 0
    private var gain = 0
    private fun <T : AudioEffect> create(factory: () -> T, configure: T.() -> Unit): T? {
        val effect = runCatching(factory).getOrNull() ?: return null
        return try { effect.configure(); effect } catch (_: Exception) { runCatching { effect.release() }; null }
    }
    val loudnessGainMb: Int @Synchronized get() = gain
    val bassStrength: Int @Synchronized get() = bass
    val spatialStrength: Int @Synchronized get() = spatial
    @Synchronized fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId in sessions) return
        val fx = Effects(create({ Equalizer(0, audioSessionId) }) { enabled = true },
            create({ BassBoost(0, audioSessionId) }) { setStrength(bass.toShort()); enabled = bass > 0 },
            create({ Virtualizer(0, audioSessionId) }) { setStrength(spatial.toShort()); enabled = spatial > 0 },
            create({ LoudnessEnhancer(audioSessionId) }) { setTargetGain(gain); enabled = gain > 0 })
        sessions[audioSessionId] = fx
    }
    @Synchronized fun detachFromSession(audioSessionId: Int) { sessions.remove(audioSessionId)?.close() }
    @Synchronized fun setBassBoostStrength(strength: Int) {
        bass = strength.coerceIn(0, 1000)
        sessions.values.forEach { runCatching { it.bass?.apply { setStrength(bass.toShort()); enabled = bass > 0 } } }
    }
    @Synchronized fun setVirtualizerStrength(strength: Int) {
        spatial = strength.coerceIn(0, 1000)
        sessions.values.forEach { runCatching { it.spatial?.apply { setStrength(spatial.toShort()); enabled = spatial > 0 } } }
    }
    @Synchronized fun setLoudnessGain(gainMb: Int) {
        gain = gainMb.coerceIn(0, 602)
        sessions.values.forEach { runCatching { it.loudness?.apply { setTargetGain(gain); enabled = gain > 0 } } }
    }
    @Synchronized fun setBandLevel(band: Short, levelMb: Short) { sessions.values.forEach { runCatching { it.eq?.setBandLevel(band, levelMb) } } }
    @Synchronized fun release() { sessions.values.forEach { it.close() }; sessions.clear() }
}
