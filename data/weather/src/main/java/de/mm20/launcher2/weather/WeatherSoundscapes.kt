package de.mm20.launcher2.weather

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

enum class SoundscapeType {
    None,
    SoftRain,
    Thunderstorm,
    OceanWaves,
    NightCrickets
}

object WeatherSoundscapes {
    private var audioTrack: AudioTrack? = null
    private var playThread: Thread? = null
    @kotlin.jvm.Volatile var activeType: SoundscapeType = SoundscapeType.None
        private set

    fun play(type: SoundscapeType) {
        stop()
        if (type == SoundscapeType.None) return
        activeType = type

        val thread = Thread {
            val sampleRate = 22050
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val samples = ShortArray(1024)
            var phase = 0.0
            val random = java.util.Random()

            while (activeType == type && !Thread.currentThread().isInterrupted) {
                var idx = 0
                while (idx < samples.size) {
                    val sample = when (type) {
                        SoundscapeType.SoftRain -> {
                            val whiteNoise = (random.nextFloat() * 2f - 1f) * 0.15f
                            val pinkFilter = (Math.sin(phase) * 0.05f) + whiteNoise
                            phase += 0.05
                            pinkFilter
                        }
                        SoundscapeType.Thunderstorm -> {
                            val noise = (random.nextFloat() * 2f - 1f) * 0.2f
                            val rumble = (Math.sin(phase * 0.02) * 0.15f) + noise
                            phase += 0.01
                            rumble
                        }
                        SoundscapeType.OceanWaves -> {
                            val waveMod = (Math.sin(phase * 0.005) * 0.5 + 0.5)
                            val noise = (random.nextFloat() * 2f - 1f) * 0.12f
                            phase += 0.01
                            (noise * waveMod)
                        }
                        SoundscapeType.NightCrickets -> {
                            val chirp = if ((phase % 500) < 100) (Math.sin(phase * 0.8) * 0.2f) else 0.0
                            phase += 1.0
                            chirp
                        }
                        SoundscapeType.None -> 0.0
                    }
                    val floatSample = Math.max(-1.0, Math.min(1.0, sample))
                    samples[idx] = (floatSample * 32767).toInt().toShort()
                    idx++
                }
                track.write(samples, 0, samples.size)
            }
        }
        playThread = thread
        thread.start()
    }

    fun stop() {
        activeType = SoundscapeType.None
        playThread?.interrupt()
        playThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (ignored: Exception) {
            // Ignore
        }
        audioTrack = null
    }
}
