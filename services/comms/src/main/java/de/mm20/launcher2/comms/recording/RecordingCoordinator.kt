package de.mm20.launcher2.comms.recording

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import rikka.shizuku.Shizuku
import java.io.DataOutputStream
import java.io.File

/**
 * Picks a capture path:
 *  - Shizuku/root grant `CAPTURE_AUDIO_OUTPUT` then [MediaRecorder.AudioSource.VOICE_CALL]
 *    (two-way mix on many OEMs, no announcement).
 *  - Unprivileged [CallAudioRecorder] (VOICE_COMMUNICATION → MIC) always as last resort.
 *
 * User preference [CommsGroup.recordingBackend] is honored; if the chosen privileged
 * backend is missing or the grant/start fails, we fall through to the next option.
 */
object RecordingCoordinator : KoinComponent {
    private const val TAG = "RecordingCoordinator"

    private val commsSettings: CommsSettings by inject()

    private val _backend = MutableStateFlow("none")
    val activeBackend = _backend.asStateFlow()

    /** Deletes recordings older than the configured retention (0 = keep forever). */
    suspend fun purgeOld(context: Context) {
        val days = commsSettings.recordingAutoDeleteDays.first()
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        // listing encrypts older recordings: file work, not for the main thread
        withContext(Dispatchers.IO) {
            for (rec in CallAudioRecorder.list(context)) {
                if (rec.file.lastModified() in 1 until cutoff) CallAudioRecorder.delete(rec.file)
            }
        }
    }

    suspend fun start(
        context: Context,
        phoneNumber: String,
        quality: RecordingQuality,
    ): Boolean {
        if (CallAudioRecorder.isRecording.value) return true
        purgeOld(context)
        val pref = commsSettings.recordingBackend.first()
        val order = when (pref) {
            "unprivileged" -> listOf("unprivileged")
            "shizuku" -> listOf("shizuku", "unprivileged")
            "root" -> listOf("root", "unprivileged")
            else -> listOf("shizuku", "root", "unprivileged")
        }
        for (backend in order) {
            val ok = when (backend) {
                "shizuku" -> tryShizuku(context, phoneNumber, quality)
                "root" -> tryRoot(context, phoneNumber, quality)
                else -> CallAudioRecorder.start(context, phoneNumber, quality, privilegedCapture = false)
            }
            if (ok) {
                _backend.value = backend
                Log.i(TAG, "Recording started via $backend")
                return true
            }
            CallAudioRecorder.stop()
        }
        _backend.value = "none"
        return false
    }

    fun stop(): File? {
        _backend.value = "none"
        return CallAudioRecorder.stop()
    }

    private suspend fun tryShizuku(
        context: Context,
        phoneNumber: String,
        quality: RecordingQuality,
    ): Boolean {
        if (!shizukuReady()) return false
        grantCaptureViaShizuku(context.packageName)
        return CallAudioRecorder.start(context, phoneNumber, quality, privilegedCapture = true)
    }

    private suspend fun tryRoot(
        context: Context,
        phoneNumber: String,
        quality: RecordingQuality,
    ): Boolean {
        if (!rootReady()) return false
        grantCaptureViaRoot(context.packageName)
        return CallAudioRecorder.start(context, phoneNumber, quality, privilegedCapture = true)
    }

    private fun shizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun rootReady(): Boolean = withContext(Dispatchers.IO) {
        runShell(arrayOf("su", "-c", "id"), asRootBinary = true)
    }

    private suspend fun grantCaptureViaShizuku(packageName: String) = withContext(Dispatchers.IO) {
        val uid = Process.myUid()
        val cmd =
            "appops set --uid $uid $packageName CAPTURE_AUDIO_OUTPUT allow; " +
                "appops set $packageName CAPTURE_AUDIO_OUTPUT allow; " +
                "appops set $packageName RECORD_AUDIO allow"
        runShizuku(arrayOf("sh", "-c", cmd))
    }

    private suspend fun grantCaptureViaRoot(packageName: String) = withContext(Dispatchers.IO) {
        val uid = Process.myUid()
        val cmd =
            "appops set --uid $uid $packageName CAPTURE_AUDIO_OUTPUT allow; " +
                "appops set $packageName CAPTURE_AUDIO_OUTPUT allow"
        runShell(arrayOf("su", "-c", cmd), asRootBinary = true)
    }

    private fun runShizuku(command: Array<String>): Boolean {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            val process = method.invoke(null, command, null, null) ?: return false
            (process::class.java.getMethod("waitFor").invoke(process) as Int) == 0
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku appops failed", e)
            false
        }
    }

    private fun runShell(command: Array<String>, asRootBinary: Boolean): Boolean {
        return try {
            if (asRootBinary && command.size >= 3 && command[0] == "su") {
                val process = Runtime.getRuntime().exec("su")
                DataOutputStream(process.outputStream).use { stdin ->
                    stdin.writeBytes("${command.drop(2).joinToString(" ")}\n")
                    stdin.writeBytes("exit\n")
                    stdin.flush()
                }
                return process.waitFor() == 0
            }
            Runtime.getRuntime().exec(command).waitFor() == 0
        } catch (e: Exception) {
            Log.w(TAG, "shell failed", e)
            false
        }
    }
}
