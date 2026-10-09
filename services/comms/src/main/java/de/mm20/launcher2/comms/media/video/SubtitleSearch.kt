package de.mm20.launcher2.comms.media.video

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** What is looked for: parsed from the file name, plus the file hash when the file can be read. */
data class SubtitleQuery(
    val title: String,
    val season: Int?,
    val episode: Int?,
    val year: Int?,
    /** two letter codes, the most wanted first */
    val languages: List<String>,
    val fileName: String,
    val movieHash: String? = null,
    val byteSize: Long = 0L,
)

data class SubtitleResult(
    /** [SubtitleProvider.id] of the source */
    val provider: String,
    val id: String,
    /** two letter code in lower case */
    val language: String,
    val release: String,
    val downloads: Int,
    val fileName: String,
    val hearingImpaired: Boolean,
    /** found by the hash of the video file, so it fits this exact file */
    val hashMatch: Boolean = false,
    val downloadUrl: String = "",
    /** character set named by the source, if any */
    val encoding: String? = null,
    val format: String = "srt",
)

/** One place subtitles can be found. Searching and downloading never throw for "nothing found". */
interface SubtitleProvider {
    val id: String
    suspend fun search(q: SubtitleQuery): List<SubtitleResult>
    /** The raw downloaded bytes (may be zipped or gzipped, see [SubtitleFiles]) */
    suspend fun download(r: SubtitleResult): ByteArray
}

internal object Http {
    /**
     * Addresses come out of answers of the sources: only http(s) addresses of the public internet are
     * followed, never ones of this phone or of the local network.
     */
    fun isPublicWeb(url: String): Boolean {
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (u.scheme?.lowercase() !in setOf("http", "https")) return false
        val host = (u.host ?: return false).lowercase()
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) return false
        if (host.contains(':')) {
            val h = host.trim('[', ']')
            return !(h == "::1" || h == "::" || h.startsWith("fe80") || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("::ffff:"))
        }
        val m = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(host) ?: return true
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        return !(a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) || (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) || (a == 100 && b in 64..127))
    }

    fun open(url: String, userAgent: String, accept: String = "application/json"): HttpURLConnection {
        if (!isPublicWeb(url)) throw java.io.IOException("This address is not allowed")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", userAgent)
        c.setRequestProperty("Accept", accept)
        return c
    }

    fun readText(c: HttpURLConnection): String = try {
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error(JSONObject(runCatching { JSONObject(text) }.getOrNull()?.toString() ?: "{}").optString("message", "HTTP $code"))
        text
    } finally {
        c.disconnect()
    }

    /** At most 6 MB, which is far more than any subtitle */
    fun readBytes(c: HttpURLConnection): ByteArray = try {
        val code = c.responseCode
        if (code !in 200..299) error("HTTP $code")
        c.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > 6_000_000) error("The subtitle file is too big")
            }
            out.toByteArray()
        }
    } finally {
        c.disconnect()
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/**
 * The older OpenSubtitles API on rest.opensubtitles.org. It needs no account and no key, only a
 * User-Agent: the generic "TemporaryUserAgent" works for testing, a registered one (free, on
 * opensubtitles.org) is the proper way. OpenSubtitles has announced this API as deprecated.
 */
class OpenSubtitlesLegacyProvider(private val userAgent: String) : SubtitleProvider {
    override val id get() = ID

    override suspend fun search(q: SubtitleQuery): List<SubtitleResult> = withContext(Dispatchers.IO) {
        val langs = q.languages.mapNotNull { SubtitleLanguages.toOpenSubtitles(it) }.distinct().joinToString(",")
        val found = LinkedHashMap<String, SubtitleResult>()
        var firstError: Throwable? = null
        for (url in legacyUrls(q, langs)) {
            try {
                val text = Http.readText(Http.open(url, userAgent))
                parse(text, hashSearch = url.contains("/moviehash-")).forEach { found.putIfAbsent(it.id, it) }
            } catch (e: Throwable) {
                firstError = firstError ?: e
            }
            if (found.isNotEmpty() && url.contains("/moviehash-")) break
        }
        if (found.isEmpty() && firstError != null) throw firstError
        found.values.toList()
    }

    override suspend fun download(r: SubtitleResult): ByteArray = withContext(Dispatchers.IO) {
        Http.readBytes(Http.open(r.downloadUrl, userAgent, "*/*"))
    }

    companion object {
        const val ID = "opensubtitles_legacy"
        const val GENERIC_AGENT = "TemporaryUserAgent"

        /** The search addresses in the order they are tried: by file hash, then by name */
        fun legacyUrls(q: SubtitleQuery, languages: String): List<String> {
            val base = "https://rest.opensubtitles.org/search/"
            val lang = if (languages.isBlank()) "" else "/sublanguageid-$languages"
            val urls = mutableListOf<String>()
            if (!q.movieHash.isNullOrBlank() && q.byteSize > 0) {
                urls += base + "moviebytesize-${q.byteSize}/moviehash-${q.movieHash}$lang"
            }
            if (q.title.isNotBlank()) {
                val seg = StringBuilder()
                if (q.season != null && q.episode != null) seg.append("episode-${q.episode}/")
                seg.append("query-${Http.enc(q.title.lowercase())}")
                if (q.season != null && q.episode != null) seg.append("/season-${q.season}")
                urls += base + seg + lang
            }
            return urls
        }

        fun parse(json: String, hashSearch: Boolean): List<SubtitleResult> {
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val link = o.optString("SubDownloadLink")
                    if (link.isBlank()) continue
                    add(
                        SubtitleResult(
                            provider = ID,
                            id = o.optString("IDSubtitleFile"),
                            language = o.optString("ISO639").lowercase(),
                            release = o.optString("MovieReleaseName"),
                            downloads = o.optString("SubDownloadsCnt").toIntOrNull() ?: 0,
                            fileName = o.optString("SubFileName"),
                            hearingImpaired = o.optString("SubHearingImpaired") == "1",
                            hashMatch = hashSearch && o.optString("MatchedBy") == "moviehash",
                            downloadUrl = link,
                            encoding = o.optString("SubEncoding").ifBlank { null },
                            format = o.optString("SubFormat").ifBlank { "srt" },
                        )
                    )
                }
            }
        }
    }
}

/**
 * Podnapisi (podnapisi.net): public search, no account. The addresses follow the public web
 * interface; they could not be checked from the build environment, so parsing is defensive.
 */
class PodnapisiProvider : SubtitleProvider {
    override val id get() = ID

    override suspend fun search(q: SubtitleQuery): List<SubtitleResult> = withContext(Dispatchers.IO) {
        if (q.title.isBlank()) return@withContext emptyList()
        val params = mutableListOf("keywords=" + Http.enc(q.title))
        q.languages.forEach { params += "language=" + Http.enc(it) }
        if (q.season != null && q.episode != null) {
            params += "seasons=${q.season}"
            params += "episodes=${q.episode}"
        } else q.year?.let { params += "year=$it" }
        val url = "https://www.podnapisi.net/subtitles/search/advanced?" + params.joinToString("&")
        parse(Http.readText(Http.open(url, "Telos Video", "application/json")))
    }

    override suspend fun download(r: SubtitleResult): ByteArray = withContext(Dispatchers.IO) {
        Http.readBytes(Http.open(r.downloadUrl, "Telos Video", "*/*"))
    }

    companion object {
        const val ID = "podnapisi"
        private const val BASE = "https://www.podnapisi.net"

        fun parse(json: String): List<SubtitleResult> {
            val data = runCatching { JSONObject(json).optJSONArray("data") }.getOrNull() ?: return emptyList()
            return buildList {
                for (i in 0 until data.length()) {
                    val o = data.optJSONObject(i) ?: continue
                    val sid = o.optString("id").ifBlank { continue }
                    val releases = o.optJSONArray("releases")
                    val release = if (releases != null && releases.length() > 0) releases.optString(0)
                    else o.optString("release").ifBlank { o.optString("title") }
                    val flags = o.optJSONArray("flags")
                    val hi = (0 until (flags?.length() ?: 0)).any { flags!!.optString(it).contains("hearing", true) }
                    val rel = o.optString("download")
                    add(
                        SubtitleResult(
                            provider = ID,
                            id = sid,
                            language = o.optString("language").lowercase().take(2),
                            release = release,
                            downloads = o.optJSONObject("stats")?.optInt("downloads") ?: o.optInt("downloads"),
                            fileName = release,
                            hearingImpaired = hi,
                            downloadUrl = when {
                                rel.startsWith("http") -> rel
                                rel.startsWith("/") -> BASE + rel
                                else -> "$BASE/subtitles/$sid/download"
                            },
                        )
                    )
                }
            }
        }
    }
}

/**
 * OpenSubtitles.com (new API). Needs the user's own API key and, for downloads, an account;
 * kept as an optional source.
 */
class OpenSubtitlesComProvider(
    private val apiKey: String,
    private val user: String,
    private val password: String,
) : SubtitleProvider {
    override val id get() = ID
    val configured: Boolean get() = apiKey.isNotBlank()

    private fun connection(url: String, post: Boolean = false): HttpURLConnection {
        val c = Http.open(url, "Telos Video v1")
        c.setRequestProperty("Api-Key", apiKey)
        if (post) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
        }
        return c
    }

    override suspend fun search(q: SubtitleQuery): List<SubtitleResult> = withContext(Dispatchers.IO) {
        if (!configured) return@withContext emptyList()
        val params = sortedMapOf<String, String>()
        params["query"] = q.title
        params["languages"] = q.languages.joinToString(",").ifBlank { "en" }
        if (q.season != null && q.episode != null) {
            params["season_number"] = q.season.toString()
            params["episode_number"] = q.episode.toString()
            params["type"] = "episode"
        } else {
            q.year?.let { params["year"] = it.toString() }
            params["type"] = "movie"
        }
        if (!q.movieHash.isNullOrBlank()) params["moviehash"] = q.movieHash
        val query = params.entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, "UTF-8") }
        val body = Http.readText(connection("https://api.opensubtitles.com/api/v1/subtitles?$query"))
        val data = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until data.length()) {
                val a = data.getJSONObject(i).optJSONObject("attributes") ?: continue
                val file = a.optJSONArray("files")?.optJSONObject(0) ?: continue
                add(
                    SubtitleResult(
                        provider = ID,
                        id = file.optLong("file_id").toString(),
                        language = a.optString("language").lowercase().take(2),
                        release = a.optString("release"),
                        downloads = a.optInt("download_count"),
                        fileName = file.optString("file_name"),
                        hearingImpaired = a.optBoolean("hearing_impaired"),
                        hashMatch = a.optBoolean("moviehash_match"),
                    )
                )
            }
        }
    }

    @Volatile private var token: String? = null

    private fun login(): String? {
        if (user.isBlank() || password.isBlank()) return null
        token?.let { return it }
        val c = connection("https://api.opensubtitles.com/api/v1/login", post = true)
        c.outputStream.use { it.write(JSONObject().put("username", user).put("password", password).toString().toByteArray()) }
        return JSONObject(Http.readText(c)).optString("token").ifBlank { null }.also { token = it }
    }

    override suspend fun download(r: SubtitleResult): ByteArray = withContext(Dispatchers.IO) {
        val c = connection("https://api.opensubtitles.com/api/v1/download", post = true)
        login()?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        c.outputStream.use {
            it.write(JSONObject().put("file_id", r.id.toLongOrNull() ?: 0L).put("sub_format", "srt").toString().toByteArray())
        }
        val link = JSONObject(Http.readText(c)).optString("link")
        if (link.isBlank()) error("No download link")
        Http.readBytes(Http.open(link, "Telos Video v1", "*/*"))
    }

    companion object {
        const val ID = "opensubtitles_com"
    }
}

/** Writes the subtitle for the player: unzips, converts to UTF-8, and keeps it per video. */
object SubtitleFiles {
    private fun dir(context: Context) = File(context.cacheDir, "subtitles").apply { mkdirs() }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)

    /** Downloads (or finds in the cache) the subtitle and returns the UTF-8 file */
    suspend fun fetch(context: Context, provider: SubtitleProvider, r: SubtitleResult, videoKey: String?): File =
        kotlinx.coroutines.withContext(Dispatchers.IO) { fetchBlocking(context, provider, r, videoKey) }

    private suspend fun fetchBlocking(context: Context, provider: SubtitleProvider, r: SubtitleResult, videoKey: String?): File {
        val cached = File(dir(context), "${safe(r.provider)}-${safe(r.id)}.${safe(r.language)}.${safe(r.format.ifBlank { "srt" })}")
        if (cached.exists() && cached.length() > 0) {
            remember(context, videoKey, cached)
            return cached
        }
        val raw = provider.download(r)
        val (name, bytes) = SubtitleArchive.extract(raw)
        val ext = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it in SubtitleArchive.EXTENSIONS } ?: r.format.lowercase()
        val out = File(dir(context), "${safe(r.provider)}-${safe(r.id)}.${safe(r.language)}.${safe(ext)}")
        out.writeText(SubtitleText.decode(bytes, r.language, r.encoding), Charsets.UTF_8)
        remember(context, videoKey, out)
        return out
    }

    /** Imports a subtitle that the user picked: converted to UTF-8 and stored in the cache */
    fun import(context: Context, fileName: String, bytes: ByteArray, languageHint: String?, videoKey: String?): File {
        val (name, data) = SubtitleArchive.extract(bytes)
        val ext = (name ?: fileName).substringAfterLast('.', "srt").lowercase().takeIf { it in SubtitleArchive.EXTENSIONS } ?: "srt"
        val out = File(dir(context), "import-${safe(fileName.substringBeforeLast('.'))}-${bytes.size}.$ext")
        out.writeText(SubtitleText.decode(data, languageHint, null), Charsets.UTF_8)
        remember(context, videoKey, out)
        return out
    }

    /** Stable key of a video for the per video cache */
    fun videoKey(fileName: String, byteSize: Long): String = Integer.toHexString((fileName.lowercase() + "|" + byteSize).hashCode())

    private fun marker(context: Context, videoKey: String) = File(dir(context), "video-${safe(videoKey)}.txt")

    private fun remember(context: Context, videoKey: String?, file: File) {
        if (videoKey == null) return
        runCatching { marker(context, videoKey).writeText(file.name) }
    }

    /** The subtitle that was loaded last time for this video, if still in the cache */
    fun lastFor(context: Context, videoKey: String): File? = runCatching {
        File(dir(context), marker(context, videoKey).readText().trim()).takeIf { it.exists() && it.length() > 0 }
    }.getOrNull()

    /** A shifted copy (delay) of a subtitle file; [ms] positive = later */
    fun shifted(context: Context, source: File, ms: Long): File {
        if (ms == 0L) return source
        val out = File(dir(context), "shift$ms-${source.name}")
        out.writeText(SubtitleShift.shift(source.readText(Charsets.UTF_8), source.extension, ms), Charsets.UTF_8)
        return out
    }
}
