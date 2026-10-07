package de.mm20.launcher2.comms.media.video

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val fileName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val folder: String,
    val dateAddedSeconds: Long,
)

/** Reads the videos on the device from the Android media store. */
object VideoLibrary {

    suspend fun load(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
        )
        val items = mutableListOf<VideoItem>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val fileName = cursor.getString(nameCol).orEmpty()
                    items += VideoItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        title = fileName.substringBeforeLast('.'),
                        fileName = fileName,
                        durationMs = cursor.getLong(durationCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        folder = cursor.getString(bucketCol).orEmpty().ifBlank { "Other" },
                        dateAddedSeconds = cursor.getLong(addedCol),
                    )
                }
            }
        }
        items
    }
}

data class ParsedName(
    val title: String,
    val season: Int?,
    val episode: Int?,
    val year: Int?,
) {
    val isEpisode: Boolean get() = season != null && episode != null
}

/** Works out show, season, episode (or movie title and year) from a file name. */
object EpisodeParser {
    private val sxe = Regex("^(.*?)[ ._-]+[sS](\\d{1,2})[ ._-]*[eE](\\d{1,3})")
    private val nxm = Regex("^(.*?)[ ._-]+(\\d{1,2})[xX](\\d{2,3})")
    private val year = Regex("^(.*?)[ ._(\\[-]+((?:19|20)\\d{2})(?!\\d)")

    private fun clean(raw: String) = raw.replace('.', ' ').replace('_', ' ').trim(' ', '-', '(', '[')

    fun parse(fileName: String): ParsedName {
        val base = fileName.substringBeforeLast('.')
        (sxe.find(base) ?: nxm.find(base))?.let { m ->
            return ParsedName(
                title = clean(m.groupValues[1]),
                season = m.groupValues[2].toInt(),
                episode = m.groupValues[3].toInt(),
                year = null,
            )
        }
        year.find(base)?.let { m ->
            return ParsedName(clean(m.groupValues[1]), null, null, m.groupValues[2].toInt())
        }
        return ParsedName(clean(base), null, null, null)
    }
}

/** Remembers where each video was stopped so playback can continue later. */
object ResumeStore {
    private const val PREFS = "video_resume"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, uri: Uri, positionMs: Long, durationMs: Long) {
        if (de.mm20.launcher2.base.ProcessInfo.isolatedPlayer) {
            // the main process owns the stored positions
            PlayerBridge.send(context, PlayerBridge.ACTION_RESUME) {
                putString("uri", uri.toString()); putLong("pos", positionMs); putLong("dur", durationMs)
            }
            return
        }
        val key = uri.toString()
        prefs(context).edit()
            .putLong("p:$key", positionMs)
            .putLong("d:$key", durationMs)
            .putLong("t:$key", System.currentTimeMillis())
            .apply()
    }

    fun position(context: Context, uri: Uri): Long = prefs(context).getLong("p:$uri", 0L)

    /** 0..1, how much of the video was watched */
    fun progress(context: Context, uri: Uri): Float {
        val p = prefs(context)
        val duration = p.getLong("d:$uri", 0L)
        if (duration <= 0) return 0f
        return (p.getLong("p:$uri", 0L).toFloat() / duration).coerceIn(0f, 1f)
    }

    /** Uris of videos that are started but not finished, most recently watched first */
    fun continueWatching(context: Context): List<String> {
        val p = prefs(context)
        return p.all.entries
            .filter { it.key.startsWith("t:") }
            .sortedByDescending { (it.value as? Long) ?: 0L }
            .map { it.key.removePrefix("t:") }
            .filter { key ->
                val progress = progress(context, Uri.parse(key))
                progress > 0.02f && progress < 0.95f
            }
    }
}
