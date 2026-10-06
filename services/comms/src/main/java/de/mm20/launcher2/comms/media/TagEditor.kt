package de.mm20.launcher2.comms.media

import android.content.Context
import android.net.Uri
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib

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

    /** Needs write access to the file (on Android 11 and later the user grants it per file). */
    fun write(context: Context, uri: Uri, tags: Tags): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            val metadata = TagLib.getMetadata(pfd.dup().detachFd(), false)
            if (metadata == null) {
                false
            } else {
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
                TagLib.savePropertyMap(pfd.dup().detachFd(), map)
            }
        } ?: false
    }.getOrDefault(false)

    /** Replaces the cover art of the file with [image] (JPEG or PNG bytes). */
    fun writeCover(context: Context, uri: Uri, image: ByteArray, mimeType: String): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            TagLib.savePictures(
                pfd.dup().detachFd(),
                arrayOf(Picture(image, "", "Front Cover", mimeType)),
            )
        } ?: false
    }.getOrDefault(false)
}
