package de.mm20.launcher2.comms.media.video

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SubtitleResult(
    val fileId: Long,
    val language: String,
    val release: String,
    val downloads: Int,
    val fileName: String,
    val hearingImpaired: Boolean,
)

/**
 * Searches and downloads subtitles from OpenSubtitles (https://www.opensubtitles.com) with the user's
 * own API key and account, as their API requires for downloads.
 */
class SubtitleSearch(
    private val apiKey: String,
    private val user: String,
    private val password: String,
) {
    val configured: Boolean get() = apiKey.isNotBlank()

    private fun connection(url: String, post: Boolean = false): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.setRequestProperty("Api-Key", apiKey)
        c.setRequestProperty("User-Agent", "Telos Video v1")
        c.setRequestProperty("Accept", "application/json")
        if (post) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
        }
        return c
    }

    private fun read(c: HttpURLConnection): String {
        try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error(JSONObject(text.ifBlank { "{}" }).optString("message", "OpenSubtitles answered $code"))
            return text
        } finally {
            c.disconnect()
        }
    }

    suspend fun search(
        title: String,
        languages: String,
        season: Int?,
        episode: Int?,
        year: Int?,
        tmdbId: Long? = null,
    ): List<SubtitleResult> = withContext(Dispatchers.IO) {
        val params = sortedMapOf<String, String>()
        params["query"] = title
        params["languages"] = languages.ifBlank { "en" }
        if (season != null && episode != null) {
            params["season_number"] = season.toString()
            params["episode_number"] = episode.toString()
            params["type"] = "episode"
        } else {
            year?.let { params["year"] = it.toString() }
            params["type"] = "movie"
        }
        if (tmdbId != null && tmdbId > 0 && season == null) params["tmdb_id"] = tmdbId.toString()
        val query = params.entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, "UTF-8") }
        val body = read(connection("https://api.opensubtitles.com/api/v1/subtitles?$query"))
        val data = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until data.length()) {
                val a = data.getJSONObject(i).optJSONObject("attributes") ?: continue
                val file = a.optJSONArray("files")?.optJSONObject(0) ?: continue
                add(
                    SubtitleResult(
                        fileId = file.optLong("file_id"),
                        language = a.optString("language"),
                        release = a.optString("release"),
                        downloads = a.optInt("download_count"),
                        fileName = file.optString("file_name"),
                        hearingImpaired = a.optBoolean("hearing_impaired"),
                    )
                )
            }
        }.sortedByDescending { it.downloads }
    }

    @Volatile private var token: String? = null

    private fun login(): String? {
        if (user.isBlank() || password.isBlank()) return null
        token?.let { return it }
        val c = connection("https://api.opensubtitles.com/api/v1/login", post = true)
        c.outputStream.use { it.write(JSONObject().put("username", user).put("password", password).toString().toByteArray()) }
        return JSONObject(read(c)).optString("token").ifBlank { null }.also { token = it }
    }

    /** Downloads the subtitle to the cache folder and returns the file. */
    suspend fun download(context: Context, result: SubtitleResult): File = withContext(Dispatchers.IO) {
        val c = connection("https://api.opensubtitles.com/api/v1/download", post = true)
        login()?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        c.outputStream.use {
            it.write(JSONObject().put("file_id", result.fileId).put("sub_format", "srt").toString().toByteArray())
        }
        val link = JSONObject(read(c)).optString("link")
        if (link.isBlank()) error("No download link")
        val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
        val out = File(dir, "${result.fileId}.${result.language}.srt")
        val d = URL(link).openConnection() as HttpURLConnection
        d.connectTimeout = 8000
        d.readTimeout = 15000
        try {
            d.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        } finally {
            d.disconnect()
        }
        out
    }
}
