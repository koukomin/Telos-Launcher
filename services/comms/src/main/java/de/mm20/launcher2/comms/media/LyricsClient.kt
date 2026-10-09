package de.mm20.launcher2.comms.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    /** Lines with timestamps, empty when the lyrics are not synchronised */
    val synced: List<LyricLine>,
    val plain: String,
)

/**
 * Downloads lyrics from [LRCLIB](https://lrclib.net), an open lyrics database that needs no
 * account or key. Results are cached on the device.
 */
object LyricsClient {

    suspend fun fetch(
        context: Context,
        artist: String,
        title: String,
        album: String,
        durationMs: Long,
    ): Lyrics? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val dir = File(context.cacheDir, "lyrics").apply { mkdirs() }
        val key = java.security.MessageDigest.getInstance("MD5")
            .digest((artist + "|" + title + "|" + album + "|" + durationMs / 1000).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cached = File(dir, "$key.json")
        try {
            if (cached.exists()) {
                val lyrics = runCatching { parse(cached.readText()) }.getOrNull()
                if (lyrics != null) return@withContext lyrics
                cached.delete() // damaged cache entry
            }
            val json = download(artist, title, album, durationMs) ?: return@withContext null
            val lyrics = parse(json) ?: return@withContext null
            // write to a temporary file first so an interrupted write never leaves a broken entry
            runCatching {
                val tmp = File(dir, "$key.tmp")
                tmp.writeText(json)
                if (!tmp.renameTo(cached)) tmp.delete()
            }
            lyrics
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun download(artist: String, title: String, album: String, durationMs: Long): String? {
        val uri = Uri.Builder()
            .scheme("https")
            .authority("lrclib.net")
            .appendPath("api")
            .appendPath("get")
            .appendQueryParameter("track_name", title)
            .apply {
                if (artist.isNotBlank()) appendQueryParameter("artist_name", artist)
                if (album.isNotBlank()) appendQueryParameter("album_name", album)
                if (durationMs > 0) appendQueryParameter("duration", (durationMs / 1000).toString())
            }
            .build()
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "Telos Launcher")
            if (connection.responseCode != 200) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: String): Lyrics? {
        val o = JSONObject(json)
        val plain = o.optString("plainLyrics")
        val synced = parseLrc(o.optString("syncedLyrics"))
        return if (plain.isBlank() && synced.isEmpty()) null else Lyrics(synced, plain)
    }

    private val timestamp = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")

    /** Reads LRC text such as `[01:23.45] some words` into timed lines */
    fun parseLrc(text: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (raw in text.lineSequence()) {
            val stamps = timestamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val words = raw.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val fraction = m.groupValues[3]
                val millis = if (fraction.isEmpty()) 0L else (fraction.padEnd(3, '0').take(3)).toLong()
                lines += LyricLine((minutes * 60 + seconds) * 1000 + millis, words)
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
