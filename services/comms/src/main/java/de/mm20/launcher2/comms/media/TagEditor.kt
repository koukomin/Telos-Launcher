package de.mm20.launcher2.comms.media

import android.content.Context
import android.net.Uri
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import java.io.File

/** Reads and writes the tags of audio files (MP3, FLAC, M4A, OGG, ...) with TagLib. */
object TagEditor {

    data class Tags(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val albumArtist: String = "",
        val genre: String = "",
        val year: String = "",
        val trackNumber: String = "",
    )

    fun read(context: Context, uri: Uri): Tags? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val metadata = TagLib.getMetadata(pfd.dup().detachFd(), false)
            if (metadata == null) {
                null
            } else {
                fun value(key: String) = metadata.propertyMap[key]?.firstOrNull().orEmpty()
                Tags(
                    title = value("TITLE"),
                    artist = value("ARTIST"),
                    album = value("ALBUM"),
                    albumArtist = value("ALBUMARTIST"),
                    genre = value("GENRE"),
                    year = value("DATE"),
                    trackNumber = value("TRACKNUMBER"),
                )
            }
        }
    }.getOrNull()

    /**
     * Runs [edit] on the file behind [uri] without ever leaving a half-written file behind: the original
     * is copied to the cache first, and if the edit fails or the result can no longer be read, the
     * original bytes are written back. Needs write access to the file (on Android 11 and later the
     * user grants it per file); a read-only file or provider simply returns false.
     */
    @Synchronized
    private fun safeEdit(context: Context, uri: Uri, edit: (fd: () -> Int) -> Boolean): Boolean {
        val resolver = context.contentResolver
        val backup = File.createTempFile("tagbackup", ".bin", context.cacheDir)
        try {
            val copied = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    backup.outputStream().use { out -> input.copyTo(out) }
                    true
                } ?: false
            }.getOrDefault(false)
            if (!copied) return false

            val ok = runCatching {
                resolver.openFileDescriptor(uri, "rw")?.use { pfd -> edit { pfd.dup().detachFd() } } ?: false
            }.getOrDefault(false)
            // the edited file must still be readable, otherwise it is considered damaged
            if (ok && read(context, uri) != null) return true

            // restore the original content
            runCatching {
                resolver.openOutputStream(uri, "wt")?.use { out ->
                    backup.inputStream().use { it.copyTo(out) }
                }
            }
            return false
        } finally {
            backup.delete()
        }
    }

    fun write(context: Context, uri: Uri, tags: Tags): Boolean = safeEdit(context, uri) { fd ->
        val metadata = TagLib.getMetadata(fd(), false) ?: return@safeEdit false
        val map = metadata.propertyMap
        fun put(key: String, value: String) {
            if (value.isBlank()) map.remove(key) else map[key] = arrayOf(value.trim())
        }
        put("TITLE", tags.title)
        put("ARTIST", tags.artist)
        put("ALBUM", tags.album)
        put("ALBUMARTIST", tags.albumArtist)
        put("GENRE", tags.genre)
        put("DATE", tags.year)
        put("TRACKNUMBER", tags.trackNumber)
        TagLib.savePropertyMap(fd(), map)
    }

    /** Replaces the cover art of the file with [image] (JPEG or PNG bytes). */
    fun writeCover(context: Context, uri: Uri, image: ByteArray, mimeType: String): Boolean {
        if (image.isEmpty()) return false
        return safeEdit(context, uri) { fd ->
            TagLib.savePictures(fd(), arrayOf(Picture(image, "", "Front Cover", mimeType)))
        }
    }
}
