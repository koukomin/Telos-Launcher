package de.mm20.launcher2.comms.media.video

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Poster and description of a movie or a series, from The Movie Database. */
data class VideoMeta(
    val tmdbId: Long,
    val isSeries: Boolean,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val year: String,
    val rating: Double,
)

/**
 * Looks up posters and descriptions at TMDB (https://www.themoviedb.org) with the user's own API key
 * and keeps the answers on the device, so every title is asked for only once.
 * This product uses the TMDB API but is not endorsed or certified by TMDB.
 */
object VideoMetadata {

    private val memory = HashMap<String, VideoMeta?>()

    private fun cacheFile(context: Context) = File(context.filesDir, "video_metadata.json")

    /** Answers from TMDB and from Wikipedia are kept apart, so adding a TMDB key later gets real posters */
    private fun key(series: Boolean, title: String, year: Int?, tmdb: Boolean) =
        (if (tmdb) "tmdb:" else "wiki:") + (if (series) "tv:" else "movie:") + title.lowercase().trim() + (year?.let { ":$it" } ?: "")

    @Synchronized
    private fun loadDisk(context: Context): JSONObject =
        runCatching { JSONObject(cacheFile(context).readText()) }.getOrDefault(JSONObject())

    @Synchronized
    private fun saveDisk(context: Context, k: String, meta: VideoMeta?) {
        val all = loadDisk(context)
        all.put(
            k,
            if (meta == null) JSONObject().put("none", System.currentTimeMillis())
            else JSONObject()
                .put("id", meta.tmdbId).put("series", meta.isSeries).put("title", meta.title)
                .put("overview", meta.overview).put("poster", meta.posterUrl ?: "")
                .put("year", meta.year).put("rating", meta.rating)
        )
        runCatching { cacheFile(context).writeText(all.toString()) }
    }

    private fun fromJson(o: JSONObject) = VideoMeta(
        tmdbId = o.optLong("id"),
        isSeries = o.optBoolean("series"),
        title = o.optString("title"),
        overview = o.optString("overview"),
        posterUrl = o.optString("poster").ifBlank { null },
        year = o.optString("year"),
        rating = o.optDouble("rating", 0.0),
    )

    /** Cached answer, or null when nothing is known (yet) */
    @Synchronized
    fun cached(context: Context, series: Boolean, title: String, year: Int?, tmdb: Boolean): VideoMeta? {
        val k = key(series, title, year, tmdb)
        if (memory.containsKey(k)) return memory[k]
        val o = loadDisk(context).optJSONObject(k) ?: return null
        val meta = if (o.has("none")) null else fromJson(o)
        memory[k] = meta
        return meta
    }

    /** True when this title was already looked up (even if nothing was found) */
    @Synchronized
    fun known(context: Context, series: Boolean, title: String, year: Int?, tmdb: Boolean): Boolean {
        val k = key(series, title, year, tmdb)
        return memory.containsKey(k) || loadDisk(context).has(k)
    }

    suspend fun lookup(
        context: Context,
        apiKey: String,
        series: Boolean,
        title: String,
        year: Int?,
        language: String = "en",
    ): VideoMeta? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val tmdb = apiKey.isNotBlank()
        if (known(context, series, title, year, tmdb)) return@withContext cached(context, series, title, year, tmdb)
        val k = key(series, title, year, tmdb)
        try {
            if (!tmdb) {
                val meta = wikipedia(series, title, year)
                synchronized(this@VideoMetadata) { memory[k] = meta }
                saveDisk(context, k, meta)
                return@withContext meta
            }
            val kind = if (series) "tv" else "movie"
            val yearParam = when {
                year == null -> ""
                series -> "&first_air_date_year=$year"
                else -> "&year=$year"
            }
            val url = "https://api.themoviedb.org/3/search/$kind?api_key=${URLEncoder.encode(apiKey, "UTF-8")}" +
                "&query=${URLEncoder.encode(title, "UTF-8")}&language=$language$yearParam"
            val body = get(url)
            val first = JSONObject(body).optJSONArray("results")?.optJSONObject(0)
            val meta = first?.let {
                VideoMeta(
                    tmdbId = it.optLong("id"),
                    isSeries = series,
                    title = it.optString(if (series) "name" else "title"),
                    overview = it.optString("overview"),
                    posterUrl = it.optString("poster_path").takeIf { p -> p.isNotBlank() && p != "null" }
                        ?.let { p -> "https://image.tmdb.org/t/p/w342$p" },
                    year = it.optString(if (series) "first_air_date" else "release_date").take(4),
                    rating = it.optDouble("vote_average", 0.0),
                )
            }
            synchronized(this@VideoMetadata) { memory[k] = meta }
            saveDisk(context, k, meta)
            meta
        } catch (e: Exception) {
            // network trouble: do not remember, try again next time
            null
        }
    }

    /**
     * Without a TMDB key the lead image and the first paragraph of the Wikipedia article are used:
     * no account is needed. The article is only taken when its title contains the searched title.
     */
    private fun wikipedia(series: Boolean, title: String, year: Int?): VideoMeta? {
        val search = when {
            series -> "$title television series"
            year != null -> "$title $year film"
            else -> "$title film"
        }
        val url = "https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search" +
            "&gsrlimit=3&gsrsearch=${URLEncoder.encode(search, "UTF-8")}&prop=pageimages%7Cextracts" +
            "&piprop=thumbnail&pithumbsize=342&exintro=1&explaintext=1&exsentences=3&exlimit=3"
        val pages = JSONObject(get(url)).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val wanted = title.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val candidates = pages.keys().asSequence().map { pages.getJSONObject(it) }
            .sortedBy { it.optInt("index", 99) }
            .filter { page ->
                page.optString("title").lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").contains(wanted)
            }
            .toList()
        val page = candidates.firstOrNull { it.has("thumbnail") } ?: candidates.firstOrNull() ?: return null
        return VideoMeta(
            tmdbId = 0,
            isSeries = series,
            title = page.optString("title").substringBefore(" ("),
            overview = page.optString("extract"),
            posterUrl = page.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null },
            year = year?.toString().orEmpty(),
            rating = 0.0,
        )
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "Telos Video/1.0 (https://github.com/koukomin/Telos-Launcher)")
        try {
            if (c.responseCode !in 200..299) error("TMDB answered ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
