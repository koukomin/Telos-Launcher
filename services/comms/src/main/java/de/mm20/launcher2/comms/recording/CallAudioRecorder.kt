package de.mm20.launcher2.comms.recording

import de.mm20.launcher2.base.containedScope
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

enum class RecordingQuality(val key: String, val bitRate: Int, val sampleRate: Int) {
    COMPACT("COMPACT", 24_000, 16_000),
    BALANCED("BALANCED", 48_000, 16_000),
    HIGH("HIGH", 96_000, 44_100),
    ;

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key.equals(key, true) } ?: BALANCED
    }
}

data class CallRecordingFile(
    val file: File,
    val number: String,
    val durationHintSeconds: Long,
)

object CallAudioRecorder {
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val durationSeconds = _duration.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var timerJob: Job? = null
    private val scope = containedScope(Dispatchers.Main)
    private val stopping = AtomicBoolean(false)

    fun start(
        context: Context,
        phoneNumber: String,
        quality: RecordingQuality = RecordingQuality.BALANCED,
        privilegedCapture: Boolean = false,
    ): Boolean {
        synchronized(this) {
            if (_isRecording.value) return true
            stopping.set(false)
            val dir = File(context.filesDir, "CallRecordings").apply { mkdirs() }
            val digits = phoneNumber.filter { it.isDigit() }.ifEmpty { "Unknown" }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "REC_${digits}_$stamp.m4a")
            val unprivileged = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION to quality.sampleRate,
                MediaRecorder.AudioSource.MIC to quality.sampleRate,
                MediaRecorder.AudioSource.VOICE_RECOGNITION to quality.sampleRate,
                MediaRecorder.AudioSource.DEFAULT to 16_000,
            )
            val privileged = listOf(
                MediaRecorder.AudioSource.VOICE_CALL to quality.sampleRate,
                MediaRecorder.AudioSource.VOICE_DOWNLINK to quality.sampleRate,
                MediaRecorder.AudioSource.VOICE_UPLINK to quality.sampleRate,
            )
            val sources = if (privilegedCapture) privileged + unprivileged else unprivileged
            var recorder: MediaRecorder? = null
            for ((src, rate) in sources) {
                var rec: MediaRecorder? = null
                try {
                    rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }
                    rec.setAudioSource(src)
                    rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    rec.setAudioSamplingRate(rate)
                    rec.setAudioEncodingBitRate(quality.bitRate)
                    rec.setOutputFile(file.absolutePath)
                    rec.prepare()
                    rec.start()
                    recorder = rec
                    break
                } catch (_: Exception) {
                    // the failed recorder must be released, or every failed source leaks one
                    try {
                        rec?.release()
                    } catch (_: Exception) {
                    }
                    recorder = null
                }
            }
            if (recorder == null) {
                if (file.exists() && file.length() == 0L) file.delete()
                return false
            }
            mediaRecorder = recorder
            outputFile = file
            _isRecording.value = true
            _duration.value = 0
            timerJob = scope.launch {
                while (isActive) {
                    delay(1000)
                    _duration.value += 1
                }
            }
            return true
        }
    }

    fun stop(): File? {
        synchronized(this) {
            if (!_isRecording.value || !stopping.compareAndSet(false, true)) {
                return outputFile?.takeIf { it.exists() && it.length() > 128L }
            }
            timerJob?.cancel()
            timerJob = null
            val file = outputFile
            try {
                mediaRecorder?.stop()
            } catch (_: Exception) {
            }
            try {
                mediaRecorder?.reset()
            } catch (_: Exception) {
            }
            try {
                mediaRecorder?.release()
            } catch (_: Exception) {
            }
            mediaRecorder = null
            outputFile = null
            _isRecording.value = false
            _duration.value = 0
            if (file == null || !file.exists() || file.length() <= 128L) {
                file?.delete()
                return null
            }
            // the recording is not left readable in the app's files
            // off the main thread (the call service stops the recording there); list() finishes it if the process dies first
            Thread { RecordingCrypto.encryptInPlace(file) }.start()
            return file
        }
    }

    fun list(context: Context): List<CallRecordingFile> {
        val dir = File(context.filesDir, "CallRecordings")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension.equals("m4a", true) && it.length() > 128L }
            // recordings of an older version are encrypted now (not the one that is being recorded)
            ?.onEach { if (it != outputFile) RecordingCrypto.encryptInPlace(it) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val digits = file.nameWithoutExtension.removePrefix("REC_").split("_").firstOrNull().orEmpty()
                CallRecordingFile(file, digits.ifBlank { "Unknown" }, 0L)
            }
            .orEmpty()
    }

    fun delete(file: File): Boolean = try {
        file.delete()
    } catch (_: Exception) {
        false
    }
}
