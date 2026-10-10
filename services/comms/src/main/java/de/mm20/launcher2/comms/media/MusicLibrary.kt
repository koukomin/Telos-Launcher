package de.mm20.launcher2.comms.media

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the music on the device from the Android media store. */
object MusicLibrary {

    suspend fun load(context: Context): List<MusicTrack> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val withGenre = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val projection = listOfNotNull(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            if (withGenre) MediaStore.Audio.Media.GENRE else null,
        ).toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val tracks = mutableListOf<MusicTrack>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val genreCol = if (withGenre) cursor.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val artist = cursor.getString(artistCol).orEmpty()
                    tracks += MusicTrack(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        title = cursor.getString(titleCol).orEmpty(),
                        artist = if (artist == "<unknown>") "" else artist,
                        album = cursor.getString(albumCol).orEmpty(),
                        albumId = cursor.getLong(albumIdCol),
                        durationMs = cursor.getLong(durationCol),
                        trackNumber = cursor.getInt(trackCol) % 1000,
                        year = cursor.getInt(yearCol),
                        dateAddedSeconds = cursor.getLong(addedCol),
                        genre = if (genreCol >= 0) cursor.getString(genreCol).orEmpty().trim() else "",
                    )
                }
            }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        tracks
    }
}
