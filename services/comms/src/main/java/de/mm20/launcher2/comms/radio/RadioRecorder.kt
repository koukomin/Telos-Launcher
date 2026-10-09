package de.mm20.launcher2.comms.radio

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A recording made by Telos Radio. [id] is stable for the lifetime of the file. */
data class RadioRecording(
    val id: String,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val dateMs: Long,
    val mimeType: String,
)

/**
 * Records the raw bytes of a station's stream through a second connection (the player keeps its own).
 * The bytes go to app-private storage while recording; when it ends they are moved to Music/Telos Radio.
 * Lives in the app process like [RadioSleepTimer]; [RadioRecordingService] shows the notification.
 */
object RadioRecorder {

    sealed interface State {
        data object Idle : State
        data class Recording(
            val stationId: String,
            val stationName: String,
            val startedAt: Long,
            val bytes: Long,
        ) : State
    }

    enum class Kind { SAVED, SAVED_STREAM_ENDED, SAVED_STORAGE_FULL, NOTHING, HLS, FAILED, SAVE_FAILED }

    data class Result(val kind: Kind, val fileName: String = "")

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val _result = MutableStateFlow<Result?>(null)
    /** Outcome of the last recording; the UI shows it once and calls [consumeResult] */
    val result: StateFlow<Result?> = _result

    fun consumeResult() { _result.value = null }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var connection: java.net.HttpURLConnection? = null
    @Volatile private var stopRequested = false

    /** HLS playlists are segmented, there is no single byte stream to capture */
    fun isRecordable(url: String): Boolean = !url.contains(".m3u8", ignoreCase = true)

    @Synchronized
    fun start(context: Context, stationId: String, stationName: String, url: String): Boolean {
        if (_state.value is State.Recording) return false
        val app = context.applicationContext
        val startedAt = System.currentTimeMillis()
        stopRequested = false
        _result.value = null
        _state.value = State.Recording(stationId, stationName, startedAt, 0L)
        runCatching {
            ContextCompat.startForegroundService(
                app,
                Intent(app, RadioRecordingService::class.java)
                    .putExtra(RadioRecordingService.EXTRA_NAME, stationName)
                    .putExtra(RadioRecordingService.EXTRA_STARTED_AT, startedAt)
            )
        }
        job = scope.launch { capture(app, stationId, stationName, url, startedAt) }
        return true
    }

    /** Ends the recording; what was captured so far is kept */
    fun stop() {
        stopRequested = true
        // a blocking read cannot be cancelled, closing the connection makes it return
        runCatching { connection?.disconnect() }
    }

    private fun capture(app: Context, stationId: String, stationName: String, url: String, startedAt: Long) {
        val dir = File(app.filesDir, "radio_recordings").apply { mkdirs() }
        val tmp = File(dir, "rec_$startedAt.part")
        var kind = Kind.SAVED
        var ext = "mp3"
        var mime = "audio/mpeg"
        var bytes = 0L
        try {
            val conn = StreamResolver.open(url)
            connection = conn
            if (stopRequested) conn.disconnect()
            val code = conn.responseCode
            val type = conn.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            if (code !in 200..299) {
                kind = Kind.FAILED
            } else if (type.contains("mpegurl") || type.contains("x-scpls")) {
                kind = Kind.HLS
            } else {
                val (e, m) = extensionFor(type, url)
                ext = e; mime = m
                var lastPublished = 0L
                try {
                    conn.inputStream.use { input ->
                        FileOutputStream(tmp).use { out ->
                            val buffer = ByteArray(16 * 1024)
                            while (!stopRequested) {
                                val n = input.read(buffer)
                                if (n < 0) { if (!stopRequested) kind = Kind.SAVED_STREAM_ENDED; break }
                                if (dir.usableSpace < MIN_FREE_BYTES) { kind = Kind.SAVED_STORAGE_FULL; break }
                                out.write(buffer, 0, n)
                                bytes += n
                                if (bytes - lastPublished >= 32 * 1024) {
                                    lastPublished = bytes
                                    publish(stationId, stationName, startedAt, bytes)
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    // the stop button closes the connection on purpose, anything else is a dropped stream or a full disk
                    if (!stopRequested) {
                        kind = if (e.message?.contains("ENOSPC", ignoreCase = true) == true ||
                            e.message?.contains("No space", ignoreCase = true) == true
                        ) Kind.SAVED_STORAGE_FULL else Kind.SAVED_STREAM_ENDED
                    }
                }
            }
        } catch (e: Exception) {
            if (!stopRequested && bytes == 0L) kind = Kind.FAILED
            else if (!stopRequested && kind == Kind.SAVED) kind = Kind.SAVED_STREAM_ENDED
        } finally {
            runCatching { connection?.disconnect() }
            connection = null
        }

        val captured = if (tmp.exists()) tmp.length() else 0L
        var fileName = ""
        if (kind != Kind.HLS && kind != Kind.FAILED) {
            if (captured < MIN_USEFUL_BYTES) {
                kind = Kind.NOTHING
            } else {
                val name = fileBaseName(stationName, startedAt) + "." + ext
                try {
                    fileName = RadioRecordings.save(app, tmp, name, mime)
                } catch (e: Exception) {
                    kind = Kind.SAVE_FAILED // the temporary file stays in app storage
                }
            }
        }
        if (kind != Kind.SAVE_FAILED) runCatching { tmp.delete() }
        _result.value = Result(kind, fileName)
        _state.value = State.Idle
    }

    private fun publish(stationId: String, stationName: String, startedAt: Long, bytes: Long) {
        _state.value = State.Recording(stationId, stationName, startedAt, bytes)
    }

    private fun extensionFor(type: String, url: String): Pair<String, String> = when {
        type.contains("aac") || type.contains("aacp") || type.contains("mp4") -> "aac" to "audio/aac"
        type.contains("ogg") || type.contains("opus") -> "ogg" to "audio/ogg"
        type.contains("mpeg") || type.contains("mp3") -> "mp3" to "audio/mpeg"
        url.substringBefore('?').endsWith(".aac", true) -> "aac" to "audio/aac"
        url.substringBefore('?').endsWith(".ogg", true) -> "ogg" to "audio/ogg"
        else -> "mp3" to "audio/mpeg"
    }

    internal fun fileBaseName(station: String, at: Long): String {
        val safe = station.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(80).ifEmpty { "Radio" }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(Date(at))
        return "$safe - $stamp"
    }

    private const val MIN_FREE_BYTES = 20L * 1024 * 1024
    private const val MIN_USEFUL_BYTES = 1024L
}

/** Storage of finished recordings: MediaStore (Music/Telos Radio) on Android 10+, app music folder before */
object RadioRecordings {
    private const val FOLDER = "Telos Radio"
    private const val RELATIVE = "Music/Telos Radio/"

    /** Moves [source] into the recordings folder and returns the final display name */
    internal fun save(context: Context, source: File, displayName: String, mime: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("insert failed")
            try {
                (resolver.openOutputStream(uri) ?: throw IOException("no stream")).use { out ->
                    FileInputStream(source).use { it.copyTo(out) }
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            // the name can differ when a file with the same name exists
            return resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else displayName
            } ?: displayName
        }
        val dir = legacyDir(context)
        var target = File(dir, displayName)
        var n = 1
        while (target.exists()) {
            target = File(dir, displayName.substringBeforeLast('.') + " (" + n + ")." + displayName.substringAfterLast('.'))
            n++
        }
        FileInputStream(source).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
        return target.name
    }

    private fun legacyDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        return File(base, FOLDER).apply { mkdirs() }
    }

    fun list(context: Context): List<RadioRecording> {
        val result = mutableListOf<RadioRecording>()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                context.contentResolver.query(
                    collection,
                    arrayOf(
                        MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED,
                        MediaStore.MediaColumns.MIME_TYPE,
                    ),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
                    arrayOf(RELATIVE),
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                        result += RadioRecording(
                            id = uri.toString(), uri = uri, name = c.getString(1).orEmpty(),
                            sizeBytes = c.getLong(2), dateMs = c.getLong(3) * 1000L,
                            mimeType = c.getString(4) ?: "audio/*",
                        )
                    }
                }
            } else {
                legacyDir(context).listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.forEach { f ->
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
                    result += RadioRecording(
                        id = f.absolutePath, uri = uri, name = f.name, sizeBytes = f.length(),
                        dateMs = f.lastModified(), mimeType = mimeForName(f.name),
                    )
                }
            }
        }
        return result
    }

    fun delete(context: Context, recording: RadioRecording): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.delete(recording.uri, null, null) > 0
        } else {
            File(recording.id).delete()
        }
    }.getOrDefault(false)

    private fun mimeForName(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        else -> "audio/mpeg"
    }
}
