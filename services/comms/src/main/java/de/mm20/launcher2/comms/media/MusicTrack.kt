package de.mm20.launcher2.comms.media

import android.net.Uri

data class MusicTrack(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val dateAddedSeconds: Long,
    /** Genre tag from the media store (Android 11 and newer), empty when unknown */
    val genre: String = "",
) {
    /** Album cover as exposed by the media store */
    val albumArtUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$albumId")
}
