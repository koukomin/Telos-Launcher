package de.mm20.launcher2.comms.media

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.hypot
import kotlin.math.ln

/**
 * Live FFT bars from the playing music session. UI collects [bars]; capture is off when nobody
 * is listening and when [enabled] is false.
 */
object PlaybackSpectrum {
    const val BAR_COUNT = 16

    private val _bars = MutableStateFlow(FloatArray(BAR_COUNT))
    val bars: StateFlow<FloatArray> = _bars

    @Volatile var enabled: Boolean = true

    private var visualizer: Visualizer? = null
    private var sessionId = 0
    private var listeners = 0

    @Synchronized fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == sessionId) return
        releaseVisualizer()
        sessionId = audioSessionId
        if (enabled && listeners > 0) startVisualizer()
    }

    @Synchronized fun addListener() {
        listeners++
        if (enabled && visualizer == null && sessionId > 0) startVisualizer()
    }

    @Synchronized fun removeListener() {
        listeners = (listeners - 1).coerceAtLeast(0)
        if (listeners == 0) releaseVisualizer()
    }

    @Synchronized fun setCaptureEnabled(on: Boolean) {
        enabled = on
        if (!on) {
            releaseVisualizer()
            _bars.value = FloatArray(BAR_COUNT)
        } else if (listeners > 0 && sessionId > 0) {
            startVisualizer()
        }
    }

    @Synchronized fun detach(audioSessionId: Int) {
        if (audioSessionId != sessionId) return
        releaseVisualizer()
        sessionId = 0
        _bars.value = FloatArray(BAR_COUNT)
    }

    private fun startVisualizer() {
        if (visualizer != null || sessionId <= 0) return
        visualizer = runCatching {
            Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()?.getOrNull(1) ?: 1024
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null || fft.size < 4) return
                        _bars.value = fftToBars(fft)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        }.getOrNull()
    }

    private fun releaseVisualizer() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
    }

    internal fun fftToBars(fft: ByteArray, count: Int = BAR_COUNT): FloatArray {
        val n = fft.size / 2
        val out = FloatArray(count)
        if (n < 2) return out
        for (i in 0 until count) {
            val start = 1 + i * (n - 1) / count
            val end = 1 + (i + 1) * (n - 1) / count
            var peak = 0f
            var k = start
            while (k < end && k < n) {
                val re = fft[k * 2].toInt()
                val im = fft.getOrElse(k * 2 + 1) { 0 }.toInt()
                val mag = hypot(re.toDouble(), im.toDouble()).toFloat()
                if (mag > peak) peak = mag
                k++
            }
            val db = (ln((peak + 1f).toDouble()) / ln(128.0)).toFloat().coerceIn(0f, 1f)
            out[i] = db
        }
        return out
    }
}
