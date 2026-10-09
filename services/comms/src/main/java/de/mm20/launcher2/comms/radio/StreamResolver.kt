package de.mm20.launcher2.comms.radio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds out what a station URL really is. Many stations are published as .pls or .m3u playlists,
 * which the player cannot play directly: the playlist has to be read and the real stream used.
 */
object StreamResolver {
    data class Result(
        val urls: List<String>,
        val mimeType: String,
        val playlistName: String = "",
    )

    private val playlistMimeTypes = setOf(
        "audio/x-scpls",
        "application/pls+xml",
        "audio/x-mpegurl",
        "audio/mpegurl",
    )

    suspend fun resolve(url: String): Result = withContext(Dispatchers.IO) {
        val fallback = Result(listOf(url), "")
        runCatching {
            val path = URL(url).path.lowercase()
            if (path.endsWith(".m3u8")) return@runCatching Result(listOf(url), "application/vnd.apple.mpegurl")
            val connection = open(url)
            try {
                val mime = connection.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                val isPlaylist = mime in playlistMimeTypes || path.endsWith(".pls") || path.endsWith(".m3u")
                if (!isPlaylist) return@runCatching Result(listOf(url), mime)
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(MAX_PLAYLIST_CHARS)
                    val read = reader.read(buffer)
                    if (read > 0) String(buffer, 0, read) else ""
                }
                val entries = RadioPlaylists.parse(text)
                val urls = entries.flatMap { it.urls }.distinct()
                if (urls.isEmpty()) fallback
                else Result(urls, mime, entries.firstOrNull()?.name.orEmpty())
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(fallback)
    }

    internal fun open(startUrl: String): HttpURLConnection {
        var current = startUrl
        for (attempt in 0 until MAX_REDIRECTS) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "Telos Radio")
            // a stream that answers with something HttpURLConnection does not parse ("ICY 200 OK") throws here
            val code = try { connection.responseCode } catch (e: Exception) { connection.disconnect(); throw e }
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: return connection
                current = URL(URL(current), location).toString()
                connection.disconnect()
                continue
            }
            return connection
        }
        throw IOException("Too many redirects")
    }

    private const val MAX_REDIRECTS = 6
    private const val MAX_PLAYLIST_CHARS = 65536
}
