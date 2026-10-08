package de.mm20.launcher2.downloads.media

import android.content.Context

/** Build without the yt-dlp runtime (the default): there is no backend, the media engine reports that. */
object YtDlpBackendFactory {
    fun create(context: Context): YtDlpBackend? = null
}
