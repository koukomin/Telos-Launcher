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
    /** MediaStore RELATIVE_PATH (API 29+) or the file path, used to recognise messenger and camera folders. */
    val relativePath: String = "",
) {
    /** Folder text the parser checks against its built-in blacklist (bucket name plus path). */
    val locationHint: String get() = "$relativePath/$folder"
}

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.RELATIVE_PATH
            else MediaStore.Video.Media.DATA,
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
                val pathCol = cursor.getColumnIndex(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.RELATIVE_PATH
                    else MediaStore.Video.Media.DATA
                )
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
                        folder = cursor.getString(bucketCol).orEmpty(),
                        dateAddedSeconds = cursor.getLong(addedCol),
                        relativePath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else "",
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

    /** A standalone 19xx / 20xx number: not part of a longer digit run. */
    private val yearToken = Regex("(?<!\\d)((?:19|20)\\d{2})(?!\\d)")
    private val dateAfter = Regex("^[-_./ ]\\d{1,2}[-_./ ]\\d{1,2}(?!\\d)")
    private val dateBefore = Regex("(?<!\\d)\\d{1,2}[-_./]\\d{1,2}[-_./]$")

    /** File name shapes written by cameras, messengers and screen recorders. */
    private val appShapes = listOf(
        Regex("^(vid|img|pxl|mov|pti|ptt|aud|mvi|dsc|trim|clip)[-_ ]?\\d{6,}", RegexOption.IGNORE_CASE),
        Regex("-wa\\d+", RegexOption.IGNORE_CASE),
        Regex("^video[-_ ]?\\d{4}[-_.]\\d{2}[-_.]\\d{2}", RegexOption.IGNORE_CASE),
        Regex("^\\d{8}[-_ ]\\d{4,6}"),
        Regex("^\\d{4}[-_.]\\d{2}[-_.]\\d{2}"),
        Regex("^(screen[-_ ]?recording|screenrecorder|screen[-_ ]?record|screenshot|signal|telegram|viber|messenger|fb_vid|fb_img|snapchat|instagram|received_|whatsapp)", RegexOption.IGNORE_CASE),
    )

    private val genericWords = setOf(
        "video", "videos", "vid", "img", "pxl", "mov", "clip", "movie", "screenrecording", "screenrecorder",
        "screen", "recording", "record", "recorder", "camera", "cam", "whatsapp", "wa", "viber", "telegram",
        "messenger", "facebook", "instagram", "snapchat", "signal", "received", "mvi", "pti", "ptt", "aud",
        "trim", "untitled", "new", "output", "tmp", "file", "capture", "screenshot", "fb", "vod", "reel", "story",
    )

    /** Folders (bucket name, relative path or file path) that never hold movies or series. */
    private val blockedFolders = listOf(
        "viber", "whatsapp", "telegram", "messenger", "facebook", "instagram", "snapchat", "signal",
        "camera", "dcim", "screenshots", "screen recordings", "screen_recordings", "screenrecorder",
        "screen recorder", "screenrecord", "telos", "tiktok", "twitter", "line/", "wechat", "imo",
    )

    fun isBlockedFolder(folder: String): Boolean {
        if (folder.isBlank()) return false
        val f = folder.lowercase().replace('\\', '/')
        return blockedFolders.any { f.contains(it) }
    }

    private fun clean(raw: String) = raw.replace('.', ' ').replace('_', ' ').trim(' ', '-', '(', '[')

    private fun realTitle(title: String): Boolean {
        if (title.count { it.isLetter() } < 2) return false
        val words = title.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        return words.any { it !in genericWords }
    }

    /** [folder] is the bucket / relative path of the file, when known. */
    fun parse(fileName: String, folder: String = ""): ParsedName {
        val base = fileName.substringBeforeLast('.')
        val plain = ParsedName(clean(base), null, null, null)
        if (isBlockedFolder(folder)) return plain
        (sxe.find(base) ?: nxm.find(base))?.let { m ->
            return ParsedName(
                title = clean(m.groupValues[1]),
                season = m.groupValues[2].toInt(),
                episode = m.groupValues[3].toInt(),
                year = null,
            )
        }
        if (appShapes.any { it.containsMatchIn(base) }) return plain
        val maxYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1
        for (m in yearToken.findAll(base)) {
            val y = m.groupValues[1].toInt()
            if (y < 1900 || y > maxYear) continue
            val start = m.range.first
            val end = m.range.last + 1
            if (dateAfter.containsMatchIn(base.substring(end))) continue
            if (dateBefore.containsMatchIn(base.substring(0, start))) continue
            val title = clean(base.substring(0, start))
            if (!realTitle(title)) return plain
            return ParsedName(title, null, null, y)
        }
        return plain
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

    /** Marks a video as seen (also when it was never started) */
    fun markWatched(context: Context, uri: Uri, durationMs: Long) {
        val d = durationMs.takeIf { it > 0 } ?: 1L
        prefs(context).edit().putLong("p:$uri", d).putLong("d:$uri", d).putLong("t:$uri", System.currentTimeMillis()).apply()
    }

    /** Forgets the position and the watched mark */
    fun markUnwatched(context: Context, uri: Uri) {
        prefs(context).edit().remove("p:$uri").remove("d:$uri").remove("t:$uri").apply()
    }

    fun isWatched(context: Context, uri: Uri): Boolean = progress(context, uri) >= 0.95f

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
