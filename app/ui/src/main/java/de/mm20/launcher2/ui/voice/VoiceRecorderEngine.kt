package de.mm20.launcher2.ui.voice

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import de.mm20.launcher2.comms.recording.RecordingQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The file formats of Telos Voice Recorder. Opus needs Android 10. */
enum class VoiceFormat(val key: String, val extension: String, val mime: String) {
    Aac("aac", "m4a", "audio/mp4"),
    Opus("opus", "ogg", "audio/ogg");

    val available: Boolean get() = this == Aac || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key && it.available } ?: Aac
    }
}

/** What the microphone is tuned for */
enum class VoiceMode(val key: String, val source: Int) {
    Standard("standard", MediaRecorder.AudioSource.MIC),
    /** Tuned for speech: meetings, interviews, notes */
    Voice("voice", MediaRecorder.AudioSource.VOICE_RECOGNITION);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: Standard
    }
}

/** The settings of Telos Voice Recorder, kept in the app's preferences */
class VoiceRecorderSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("telos_voice_recorder", Context.MODE_PRIVATE)

    var format: VoiceFormat
        get() = VoiceFormat.fromKey(prefs.getString("format", null))
        set(value) = prefs.edit().putString("format", value.key).apply()

    var mode: VoiceMode
        get() = VoiceMode.fromKey(prefs.getString("mode", null))
        set(value) = prefs.edit().putString("mode", value.key).apply()

    var quality: RecordingQuality
        get() = RecordingQuality.fromKey(prefs.getString("quality", null))
        set(value) = prefs.edit().putString("quality", value.key).apply()

    /** Play recordings through the loudspeaker (true) or the earpiece (false) */
    var speaker: Boolean
        get() = prefs.getBoolean("speaker", true)
        set(value) = prefs.edit().putBoolean("speaker", value).apply()
}

data class VoiceRecording(
    val file: File,
    val name: String,
    val modified: Long,
    val durationMs: Long,
    val format: VoiceFormat,
)

enum class VoiceStatus { Idle, Recording, Paused }

data class VoiceRecorderState(
    val status: VoiceStatus = VoiceStatus.Idle,
    val elapsedMs: Long = 0,
    /** The last amplitudes, 0..32767, newest last, for the waveform */
    val amplitudes: List<Int> = emptyList(),
    val error: Boolean = false,
)

/**
 * Records the microphone to a file in the app's own storage, like Telos Phone does for calls. It
 * runs in the launcher process and is kept alive by [VoiceRecorderService] while it records.
 */
object VoiceRecorderEngine {

    private val _state = MutableStateFlow(VoiceRecorderState())
    val state: StateFlow<VoiceRecorderState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var segmentStart = 0L
    private var accumulated = 0L
    private var timer: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun directory(context: Context): File = File(context.filesDir, "VoiceRecordings").apply { mkdirs() }

    @Synchronized
    fun start(context: Context): Boolean {
        if (_state.value.status != VoiceStatus.Idle) return true
        val settings = VoiceRecorderSettings(context)
        val format = settings.format
        val quality = settings.quality
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val out = File(directory(context), "Recording_$stamp.${format.extension}")
        // MIC is tried after the chosen source, so that a device that refuses one still records
        val sources = listOf(settings.mode.source, MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.DEFAULT).distinct()
        var created: MediaRecorder? = null
        for (source in sources) {
            var rec: MediaRecorder? = null
            try {
                rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
                rec.setAudioSource(source)
                when (format) {
                    VoiceFormat.Aac -> {
                        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    }
                    VoiceFormat.Opus -> {
                        rec.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                        rec.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    }
                }
                rec.setAudioSamplingRate(if (format == VoiceFormat.Opus) 48_000 else quality.sampleRate)
                rec.setAudioEncodingBitRate(quality.bitRate)
                rec.setOutputFile(out.absolutePath)
                rec.prepare()
                rec.start()
                created = rec
                break
            } catch (_: Exception) {
                runCatching { rec?.release() }
                created = null
            }
        }
        if (created == null) {
            out.delete()
            _state.value = VoiceRecorderState(error = true)
            return false
        }
        recorder = created
        file = out
        accumulated = 0
        segmentStart = System.currentTimeMillis()
        _state.value = VoiceRecorderState(VoiceStatus.Recording)
        timer = scope.launch {
            while (isActive) {
                delay(80)
                tick()
            }
        }
        return true
    }

    private fun tick() {
        val current = _state.value
        if (current.status != VoiceStatus.Recording) return
        val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        _state.value = current.copy(
            elapsedMs = accumulated + (System.currentTimeMillis() - segmentStart),
            amplitudes = (current.amplitudes + amplitude).takeLast(MAX_AMPLITUDES),
        )
    }

    @Synchronized
    fun pause() {
        if (_state.value.status != VoiceStatus.Recording) return
        runCatching { recorder?.pause() }
        accumulated += System.currentTimeMillis() - segmentStart
        _state.value = _state.value.copy(status = VoiceStatus.Paused, elapsedMs = accumulated)
    }

    @Synchronized
    fun resume() {
        if (_state.value.status != VoiceStatus.Paused) return
        runCatching { recorder?.resume() }
        segmentStart = System.currentTimeMillis()
        _state.value = _state.value.copy(status = VoiceStatus.Recording)
    }

    /** Stops and keeps the recording. Returns null if nothing usable was recorded. */
    @Synchronized
    fun stop(): File? = finish(keep = true)

    /** Stops and throws the recording away */
    @Synchronized
    fun cancel() {
        finish(keep = false)
    }

    private fun finish(keep: Boolean): File? {
        if (_state.value.status == VoiceStatus.Idle) return null
        timer?.cancel()
        timer = null
        val rec = recorder
        val out = file
        runCatching { rec?.stop() }
        runCatching { rec?.reset() }
        runCatching { rec?.release() }
        recorder = null
        file = null
        _state.value = VoiceRecorderState()
        if (out == null) return null
        if (!keep || !out.exists() || out.length() <= 128L) {
            out.delete()
            return null
        }
        return out
    }

    fun clearError() {
        if (_state.value.error) _state.value = VoiceRecorderState()
    }

    fun list(context: Context): List<VoiceRecording> {
        val retriever = MediaMetadataRetriever()
        return try {
            directory(context).listFiles()
                ?.filter { f -> VoiceFormat.entries.any { it.extension.equals(f.extension, true) } && f.length() > 128L && f != file }
                ?.sortedByDescending { it.lastModified() }
                ?.map { f ->
                    val duration = runCatching {
                        retriever.setDataSource(f.absolutePath)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    }.getOrDefault(0L)
                    VoiceRecording(f, f.nameWithoutExtension, f.lastModified(), duration, VoiceFormat.entries.first { it.extension.equals(f.extension, true) })
                }
                .orEmpty()
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** A new name for a recording. Returns the renamed file, or null if the name is empty or taken. */
    fun rename(recording: VoiceRecording, newName: String): File? {
        val clean = newName.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").take(80)
        if (clean.isEmpty()) return null
        val target = File(recording.file.parentFile, clean + "." + recording.format.extension)
        if (target.exists()) return null
        return if (recording.file.renameTo(target)) target else null
    }

    const val MAX_AMPLITUDES = 60
}
