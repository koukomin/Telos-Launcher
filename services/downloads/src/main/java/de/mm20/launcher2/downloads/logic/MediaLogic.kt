package de.mm20.launcher2.downloads.logic

import de.mm20.launcher2.downloads.MediaData
import de.mm20.launcher2.downloads.MediaStage
import de.mm20.launcher2.downloads.SubtitleMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

// Pure parts of the media (yt-dlp) engine. Unit tested in MediaLogicTest.

enum class LinkKind { Magnet, Media, File, Web, NotALink }

/** Tells what a pasted address is. Only a hint: any web page can be tried with "Analyze". */
object MediaUrls {
    /** Hosts (without "www." and without sub domains like "m.") that yt-dlp is known to handle well */
    private val mediaHosts = setOf(
        "youtube.com", "youtu.be", "youtube-nocookie.com", "vimeo.com", "dailymotion.com", "dai.ly", "twitch.tv",
        "tiktok.com", "instagram.com", "facebook.com", "fb.watch", "twitter.com", "x.com", "reddit.com", "redd.it",
        "soundcloud.com", "bandcamp.com", "mixcloud.com", "bilibili.com", "ok.ru", "vk.com", "rumble.com",
        "odysee.com", "streamable.com", "imgur.com", "pinterest.com", "tumblr.com", "arte.tv", "ted.com",
    )

    private val fileExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "iso", "apk", "exe", "msi", "deb", "pdf", "epub", "mp3", "flac", "wav",
        "ogg", "m4a", "mp4", "mkv", "webm", "avi", "mov", "jpg", "jpeg", "png", "gif", "webp", "torrent",
    )

    fun host(url: String): String? {
        val m = Regex("""^https?://([^/:?#\s]+)""", RegexOption.IGNORE_CASE).find(url.trim()) ?: return null
        return m.groupValues[1].lowercase().removePrefix("www.")
    }

    fun isKnownMediaSite(url: String): Boolean {
        val h = host(url) ?: return false
        return mediaHosts.any { h == it || h.endsWith(".$it") }
    }

    fun classify(text: String): LinkKind {
        val t = text.trim()
        if (t.isEmpty() || t.contains(Regex("\\s"))) return LinkKind.NotALink
        if (t.startsWith("magnet:?", ignoreCase = true)) return LinkKind.Magnet
        if (!LinkParser.isHttpUrl(t)) return LinkKind.NotALink
        if (isKnownMediaSite(t)) return LinkKind.Media
        val path = t.substringBefore('#').substringBefore('?').substringAfterLast('/')
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext in fileExtensions) LinkKind.File else LinkKind.Web
    }
}

// ---- analysis result

class MediaFormat(
    val id: String, val ext: String, val height: Int, val hasVideo: Boolean, val hasAudio: Boolean,
    val bytes: Long, val abr: Int,
)

class MediaEntry(val url: String, val title: String, val durationSec: Long, val thumbnail: String)

class MediaInfo(
    val title: String,
    val uploader: String,
    val thumbnail: String,
    val durationSec: Long,
    val webUrl: String,
    val extractor: String,
    val isPlaylist: Boolean,
    val formats: List<MediaFormat>,
    val entries: List<MediaEntry>,
) {
    /** Video heights on offer, tallest first */
    val heights: List<Int> get() = formats.filter { it.hasVideo && it.height > 0 }.map { it.height }.distinct().sortedDescending()
    val hasAudio: Boolean get() = formats.any { it.hasAudio } || formats.isEmpty()
}

object MediaInfoParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Parses the output of `yt-dlp -J --flat-playlist`; null when it is not a JSON object. */
    fun parse(text: String): MediaInfo? {
        val start = text.indexOf('{')
        if (start < 0) return null
        val root = try { json.parseToJsonElement(text.substring(start)).jsonObject } catch (e: Exception) { return null }
        val type = root.str("_type")
        val entriesJson = root["entries"] as? JsonArray
        val isPlaylist = type == "playlist" || entriesJson != null
        val entries = entriesJson?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val url = o.str("webpage_url").ifBlank { o.str("url") }
            if (!url.startsWith("http", ignoreCase = true)) return@mapNotNull null
            MediaEntry(url, o.str("title").ifBlank { url }, o.num("duration"), thumbnailOf(o))
        }.orEmpty()
        val formats = (root["formats"] as? JsonArray)?.mapNotNull { f ->
            val o = f as? JsonObject ?: return@mapNotNull null
            val v = o.str("vcodec").let { it.isNotEmpty() && it != "none" }
            val a = o.str("acodec").let { it.isNotEmpty() && it != "none" }
            if (!v && !a) return@mapNotNull null
            MediaFormat(
                id = o.str("format_id"), ext = o.str("ext"), height = o.num("height").toInt(), hasVideo = v, hasAudio = a,
                bytes = o.num("filesize").takeIf { it > 0 } ?: o.num("filesize_approx"), abr = o.num("abr").toInt(),
            )
        }.orEmpty()
        return MediaInfo(
            title = root.str("title"), uploader = root.str("uploader").ifBlank { root.str("channel") },
            thumbnail = thumbnailOf(root), durationSec = root.num("duration"),
            webUrl = root.str("webpage_url"), extractor = root.str("extractor_key").ifBlank { root.str("extractor") },
            isPlaylist = isPlaylist, formats = formats, entries = entries,
        )
    }

    private fun thumbnailOf(o: JsonObject): String {
        o.str("thumbnail").takeIf { it.isNotBlank() }?.let { return it }
        val list = o["thumbnails"] as? JsonArray ?: return ""
        return list.lastOrNull()?.let { (it as? JsonObject)?.str("url") }.orEmpty()
    }

    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()
    private fun JsonObject.num(key: String): Long {
        val p = this[key] as? JsonPrimitive ?: return 0
        return p.longOrNull ?: p.doubleOrNull?.toLong() ?: 0
    }
}

// ---- format selection and the command line

object MediaFormats {
    val resolutionCaps = listOf(2160, 1440, 1080, 720, 480, 360, 240)
    val audioFormats = listOf("mp3", "m4a", "opus")
    val containers = listOf("", "mp4", "mkv", "webm")

    /** The `-f` selector for the choice in [MediaData]; always falls back to the best single file */
    fun selector(m: MediaData): String = when {
        m.audioOnly -> "ba/b"
        m.heightCap > 0 -> "bv*[height<=${m.heightCap}]+ba/b[height<=${m.heightCap}]/bv*+ba/b"
        else -> "bv*+ba/b"
    }

    /** Rough size for the choice from the analysis (best video up to the cap + best audio), 0 when unknown */
    fun estimateBytes(info: MediaInfo, m: MediaData): Long {
        val audio = info.formats.filter { it.hasAudio && !it.hasVideo }.maxOfOrNull { it.bytes } ?: 0L
        if (m.audioOnly) return audio.takeIf { it > 0 } ?: info.formats.filter { it.hasAudio }.maxOfOrNull { it.bytes } ?: 0L
        val videos = info.formats.filter { it.hasVideo && (m.heightCap <= 0 || it.height in 1..m.heightCap) }
        val best = videos.maxWithOrNull(compareBy({ it.height }, { it.bytes })) ?: return 0
        return best.bytes + if (best.hasAudio) 0 else audio
    }

    /** Where yt-dlp writes: the template for [stagingDir] */
    fun outputTemplate(stagingDir: String): String = stagingDir.trimEnd('/') + "/%(title).100s [%(id)s].%(ext)s"

    const val PROGRESS_PREFIX = "[tlsprog]"
    private const val PROGRESS_TEMPLATE =
        "download:$PROGRESS_PREFIX %(progress.downloaded_bytes)s %(progress.total_bytes)s %(progress.total_bytes_estimate)s %(progress.speed)s %(progress.eta)s"

    class Env(
        val stagingDir: String,
        val cookiesFile: String? = null,
        val rateLimitBps: Long = 0,
        val proxy: String? = null,
        val userAgent: String? = null,
        val referer: String? = null,
        val headers: Map<String, String> = emptyMap(),
    )

    /** All yt-dlp arguments for downloading [url] (one video, never a playlist) */
    fun downloadArgs(url: String, m: MediaData, env: Env): List<String> {
        val a = ArrayList<String>()
        a += listOf("--no-playlist", "--newline", "--no-colors", "--progress", "--progress-template", PROGRESS_TEMPLATE)
        a += listOf("--retries", "3", "--fragment-retries", "5", "--socket-timeout", "30", "--continue", "--no-mtime", "--windows-filenames")
        a += listOf("-f", selector(m))
        if (m.audioOnly) {
            a += listOf("-x", "--audio-format", m.audioFormat.takeIf { it in audioFormats } ?: "mp3", "--audio-quality", "0")
        } else if (m.container in containers && m.container.isNotEmpty()) {
            a += listOf("--merge-output-format", m.container)
        }
        when (m.subtitles) {
            SubtitleMode.Off -> {}
            SubtitleMode.Files, SubtitleMode.Embed -> {
                a += listOf("--write-subs", "--write-auto-subs", "--sub-langs", m.subLangs.ifBlank { "en.*" })
                if (m.subtitles == SubtitleMode.Embed && !m.audioOnly) a += "--embed-subs"
            }
        }
        if (m.embedThumbnail) a += listOf("--embed-thumbnail", "--convert-thumbnails", "jpg")
        if (m.embedMetadata) a += "--embed-metadata"
        if (m.sponsorBlock) a += listOf("--sponsorblock-remove", "sponsor")
        if (env.cookiesFile != null) a += listOf("--cookies", env.cookiesFile)
        if (env.rateLimitBps > 0) a += listOf("--limit-rate", "${(env.rateLimitBps / 1024).coerceAtLeast(1)}K")
        if (!env.proxy.isNullOrBlank()) a += listOf("--proxy", env.proxy)
        if (!env.userAgent.isNullOrBlank()) a += listOf("--user-agent", env.userAgent)
        if (!env.referer.isNullOrBlank()) a += listOf("--referer", env.referer)
        for ((k, v) in env.headers) a += listOf("--add-header", "$k:$v")
        a += listOf("-o", outputTemplate(env.stagingDir))
        a += "--"
        a += url
        return a
    }

    /** Arguments for the analysis: one JSON document, playlists only flat (no per video requests) */
    fun analyzeArgs(url: String, cookiesFile: String?, proxy: String?): List<String> {
        val a = arrayListOf("-J", "--flat-playlist", "--no-warnings", "--socket-timeout", "30")
        if (cookiesFile != null) a += listOf("--cookies", cookiesFile)
        if (!proxy.isNullOrBlank()) a += listOf("--proxy", proxy)
        a += "--"
        a += url
        return a
    }
}

// ---- output lines

sealed interface MediaLine {
    /** [total] is the exact size or the estimate, -1 when unknown */
    data class Progress(val downloaded: Long, val total: Long, val speedBps: Long, val etaSeconds: Long) : MediaLine
    data class Destination(val path: String) : MediaLine
    data class Stage(val stage: MediaStage) : MediaLine
    data object Other : MediaLine
}

object YtDlpOutput {
    fun parse(line: String): MediaLine {
        val l = line.trim()
        if (l.startsWith(MediaFormats.PROGRESS_PREFIX)) {
            val p = l.removePrefix(MediaFormats.PROGRESS_PREFIX).trim().split(Regex("\\s+"))
            if (p.size < 5) return MediaLine.Other
            fun num(s: String): Long? = s.toDoubleOrNull()?.toLong()
            val downloaded = num(p[0]) ?: return MediaLine.Other
            val total = num(p[1]) ?: num(p[2]) ?: -1
            return MediaLine.Progress(downloaded, total, num(p[3]) ?: 0, num(p[4]) ?: -1)
        }
        return when {
            l.startsWith("[download] Destination:") -> MediaLine.Destination(l.substringAfter("Destination:").trim())
            l.startsWith("[Merger]") || l.startsWith("[VideoRemuxer]") || l.startsWith("[VideoConvertor]") -> MediaLine.Stage(MediaStage.Merging)
            l.startsWith("[ExtractAudio]") || l.startsWith("[FixupM4a]") -> MediaLine.Stage(MediaStage.ExtractingAudio)
            l.startsWith("[EmbedThumbnail]") || l.startsWith("[Metadata]") || l.startsWith("[EmbedSubtitle]") ||
                l.startsWith("[ThumbnailsConvertor]") || l.startsWith("[SponsorBlock]") || l.startsWith("[ModifyChapters]") ->
                MediaLine.Stage(MediaStage.Embedding)
            else -> MediaLine.Other
        }
    }
}

/**
 * Turns the lines of one yt-dlp run into overall progress. A video and an audio stream are two downloads after each
 * other, so the bytes of finished streams are added up. [expectedTotal] (from the analysis) keeps the bar from
 * jumping back when the second stream starts; 0 when unknown.
 */
class MediaProgressTracker(private val expectedTotal: Long = 0) {
    data class Snapshot(val downloaded: Long, val total: Long, val speedBps: Long, val etaSeconds: Long, val stage: MediaStage)

    private var finished = 0L
    private var current = 0L
    private var currentTotal = -1L
    private var destinations = 0
    var stage: MediaStage = MediaStage.Downloading
        private set

    /** @return a new snapshot when the line changed something worth showing, else null */
    fun feed(line: String): Snapshot? = when (val l = YtDlpOutput.parse(line)) {
        is MediaLine.Destination -> {
            if (destinations > 0) {
                finished += if (currentTotal > 0) currentTotal else current
                current = 0; currentTotal = -1
            }
            destinations++
            null
        }
        is MediaLine.Progress -> {
            current = l.downloaded
            currentTotal = l.total
            stage = MediaStage.Downloading
            val cumulative = finished + current
            val total = when {
                expectedTotal > 0 -> maxOf(expectedTotal, cumulative)
                currentTotal > 0 -> finished + currentTotal
                else -> -1
            }
            Snapshot(cumulative, total, l.speedBps, l.etaSeconds, stage)
        }
        is MediaLine.Stage -> {
            stage = l.stage
            Snapshot(finished + current, if (expectedTotal > 0) maxOf(expectedTotal, finished + current) else finished + current, 0, -1, stage)
        }
        MediaLine.Other -> null
    }
}

// ---- errors

enum class MediaError(val retryable: Boolean) {
    Unsupported(false), LoginRequired(false), Private(false), GeoBlocked(false), Unavailable(false), RateLimited(true),
    Forbidden(false), Network(true), Ffmpeg(false), DiskFull(false), FormatUnavailable(false), NotInstalled(false), Other(false),
}

object YtDlpErrors {
    /** Maps the output of a failed run to the cause the user can act on */
    fun classify(output: String): MediaError {
        val t = output.lowercase()
        return when {
            "no space left" in t || "disk quota" in t -> MediaError.DiskFull
            "unsupported url" in t -> MediaError.Unsupported
            "http error 429" in t || "too many requests" in t -> MediaError.RateLimited
            "not available in your country" in t || "geo restrict" in t || "geo-restrict" in t || "blocked it in your country" in t -> MediaError.GeoBlocked
            "private video" in t || "this video is private" in t -> MediaError.Private
            "sign in" in t || "log in" in t || "login required" in t || "members-only" in t || "members only" in t ||
                "use --cookies" in t || "confirm your age" in t || "not a bot" in t || "authentication" in t -> MediaError.LoginRequired
            "requested format is not available" in t || "no video formats found" in t -> MediaError.FormatUnavailable
            "ffmpeg" in t && ("not found" in t || "not installed" in t) -> MediaError.Ffmpeg
            "video unavailable" in t || "has been removed" in t || "http error 404" in t || "does not exist" in t || "no longer available" in t -> MediaError.Unavailable
            "http error 403" in t -> MediaError.Forbidden
            "timed out" in t || "unable to download" in t || "temporary failure" in t || "network is unreachable" in t ||
                "connection reset" in t || "connection refused" in t || "urlerror" in t || "incompleteread" in t || "remote end closed" in t -> MediaError.Network
            else -> MediaError.Other
        }
    }

    /** The last "ERROR:" line, as a short technical detail for the log, or the last line */
    fun detail(output: String): String {
        val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return (lines.lastOrNull { it.startsWith("ERROR") } ?: lines.lastOrNull().orEmpty()).take(300)
    }
}

// ---- cookies (Netscape cookies.txt)

object CookieFiles {
    const val HEADER = "# Netscape HTTP Cookie File"

    /** Number of valid cookie lines (7 tab separated fields); 0 means this is not a cookies.txt */
    fun countCookies(text: String): Int = text.lineSequence().count { l ->
        val line = l.removePrefix("#HttpOnly_")
        !line.startsWith("#") && line.split('\t').size == 7 && line.split('\t')[0].isNotBlank()
    }

    /** Builds a cookies.txt from a `Cookie:` header value (what a web view hands out) for the host of [url] */
    fun fromHeader(url: String, header: String): String {
        val host = MediaUrls.host(url) ?: return HEADER + "\n"
        val domain = "." + host.split('.').let { if (it.size > 2) it.takeLast(2).joinToString(".") else host }
        val sb = StringBuilder(HEADER).append('\n')
        for (pair in header.split(';')) {
            val i = pair.indexOf('=')
            if (i <= 0) continue
            val name = pair.substring(0, i).trim()
            val value = pair.substring(i + 1).trim()
            if (name.isEmpty()) continue
            sb.append(domain).append("\tTRUE\t/\tTRUE\t0\t").append(name).append('\t').append(value).append('\n')
        }
        return sb.toString()
    }
}

/** Display names for the files the media engine found */
object MediaFiles {
    private val temp = setOf("part", "ytdl", "temp", "tmp", "frag", "aria2")

    /** The finished outputs among the names in the staging folder (no partial or temporary files, no cookies) */
    fun finishedOutputs(names: List<String>): List<String> = names.filter { n ->
        val ext = n.substringAfterLast('.', "").lowercase()
        ext !in temp && n != "cookies.txt" && !n.endsWith(".part-Frag") && !Regex(""".*\.part-Frag\d+""").matches(n) && !n.contains(".f\\d+\\.".toRegex())
    }

    /** The main file: the biggest one that is not a subtitle or image */
    fun mainFile(namesWithSize: List<Pair<String, Long>>): String? {
        val skip = setOf("vtt", "srt", "ass", "lrc", "jpg", "jpeg", "png", "webp", "json", "description")
        return namesWithSize.filter { it.first.substringAfterLast('.', "").lowercase() !in skip }.maxByOrNull { it.second }?.first
    }
}
