package de.mm20.launcher2.comms.media.video

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Player and subtitle settings that need no secrets. They live in a small file so that the player
 * process, which cannot open the settings store, reads and writes the same values.
 */
object VideoPrefs {
    private const val FILE = "video_prefs.json"

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    @Synchronized
    private fun read(context: Context): JSONObject =
        runCatching { JSONObject(file(context).readText()) }.getOrDefault(JSONObject())

    @Synchronized
    private fun write(context: Context, change: JSONObject.() -> Unit) {
        runCatching {
            val j = read(context)
            j.change()
            val f = file(context)
            val tmp = File(f.parentFile, "$FILE.tmp")
            tmp.writeText(j.toString())
            tmp.renameTo(f)
        }
    }

    // --- subtitle sources ---
    data class SubtitleSource(val id: String, val enabled: Boolean)

    val DEFAULT_SOURCES = listOf(
        SubtitleSource(OpenSubtitlesLegacyProvider.ID, true),
        SubtitleSource(PodnapisiProvider.ID, true),
        SubtitleSource(OpenSubtitlesComProvider.ID, true),
    )

    /** Saved order and switches; sources that are new in an update are appended */
    fun subtitleSources(context: Context): List<SubtitleSource> {
        val array = read(context).optJSONArray("subSources")
        val saved = buildList {
            for (i in 0 until (array?.length() ?: 0)) {
                val o = array!!.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (DEFAULT_SOURCES.any { it.id == id }) add(SubtitleSource(id, o.optBoolean("on", true)))
            }
        }.distinctBy { it.id }
        return saved + DEFAULT_SOURCES.filter { d -> saved.none { it.id == d.id } }
    }

    fun setSubtitleSources(context: Context, sources: List<SubtitleSource>) = write(context) {
        put("subSources", JSONArray().apply { sources.forEach { put(JSONObject().put("id", it.id).put("on", it.enabled)) } })
    }

    var Context.legacyUserAgent: String
        get() = read(this).optString("osUserAgent").ifBlank { OpenSubtitlesLegacyProvider.GENERIC_AGENT }
        set(v) = write(this) { put("osUserAgent", v.trim()) }

    // --- subtitle look ---
    data class SubtitleStyle(
        /** 0.6 .. 2.0 times the normal size */
        val scale: Float = 1f,
        val color: Int = 0xFFFFFFFF.toInt(),
        /** 0 none, 1 outline, 2 shadow, 3 box behind the text */
        val edge: Int = 1,
    )

    fun subtitleStyle(context: Context): SubtitleStyle {
        val j = read(context)
        return SubtitleStyle(
            scale = j.optDouble("subScale", 1.0).toFloat().coerceIn(0.5f, 2.5f),
            color = j.optInt("subColor", 0xFFFFFFFF.toInt()),
            edge = j.optInt("subEdge", 1).coerceIn(0, 3),
        )
    }

    fun setSubtitleStyle(context: Context, s: SubtitleStyle) = write(context) {
        put("subScale", s.scale.toDouble()); put("subColor", s.color); put("subEdge", s.edge)
    }

    // --- picture ---
    /** One of the PlayerView resize modes (0 fit, 1 fixed width, 2 fixed height, 3 fill, 4 zoom) */
    fun resizeMode(context: Context): Int = read(context).optInt("resize", 0)
    fun setResizeMode(context: Context, mode: Int) = write(context) { put("resize", mode) }

    fun matchFrameRate(context: Context): Boolean = read(context).optBoolean("matchFps", true)
    fun setMatchFrameRate(context: Context, on: Boolean) = write(context) { put("matchFps", on) }

    /** Continue music or radio after a video that paused it (off by default) */
    fun resumeAfterVideo(context: Context): Boolean = read(context).optBoolean("resumeAfterVideo", false)
    fun setResumeAfterVideo(context: Context, on: Boolean) = write(context) { put("resumeAfterVideo", on) }

    // --- network sources of the library ---
    fun networkSources(context: Context): List<String> {
        val array = read(context).optJSONArray("netSources") ?: return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }

    fun setNetworkSources(context: Context, paths: List<String>) = write(context) {
        put("netSources", JSONArray(paths.distinct()))
    }
}

/** Builds the subtitle search for a video and runs the chain of sources. */
object SubtitleService {

    class Outcome(val results: List<SubtitleResult>, val source: String?, val errors: List<String>)

    private fun providers(context: Context, config: VideoServicesConfig, onlyEnabled: Boolean = true): List<SubtitleProvider> =
        VideoPrefs.subtitleSources(context).filter { !onlyEnabled || it.enabled }.mapNotNull { s ->
            when (s.id) {
                OpenSubtitlesLegacyProvider.ID -> with(VideoPrefs) { OpenSubtitlesLegacyProvider(context.legacyUserAgent) }
                PodnapisiProvider.ID -> PodnapisiProvider()
                OpenSubtitlesComProvider.ID ->
                    OpenSubtitlesComProvider(config.subtitleKey, config.subtitleUser, config.subtitlePassword).takeIf { it.configured }
                else -> null
            }
        }

    fun provider(context: Context, config: VideoServicesConfig, id: String): SubtitleProvider? =
        providers(context, config, onlyEnabled = false).firstOrNull { it.id == id }

    /** True when at least one source can be asked */
    fun available(context: Context, config: VideoServicesConfig) = providers(context, config).isNotEmpty()

    /**
     * Asks the sources in the saved order. By default it stops at the first source that has
     * something, [all] asks every source and merges.
     */
    suspend fun search(context: Context, config: VideoServicesConfig, q: SubtitleQuery, all: Boolean = false): Outcome {
        val errors = mutableListOf<String>()
        val merged = mutableListOf<SubtitleResult>()
        var source: String? = null
        for (p in providers(context, config)) {
            try {
                val r = p.search(q)
                if (r.isNotEmpty()) {
                    merged += r
                    source = source ?: p.id
                    if (!all) break
                }
            } catch (e: Throwable) {
                errors += p.id + ": " + (e.message ?: e.javaClass.simpleName)
            }
        }
        return Outcome(SubtitleRanking.rank(merged, q.languages, q.fileName), source, errors)
    }

    /** Name, season and hash of a video. [uri] may be null or not readable (web, network). */
    suspend fun queryFor(context: Context, uri: Uri?, displayName: String, languageSetting: String): SubtitleQuery =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val parsed = EpisodeParser.parse(displayName.replace(Regex("\\.(mkv|mp4|avi|mov|webm|m4v|ts|mpg|mpeg|wmv|flv)$", RegexOption.IGNORE_CASE), "") + ".x")
            var hash: String? = null
            var size = 0L
            if (uri != null && uri.scheme in listOf("content", "file")) {
                runCatching {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd: ParcelFileDescriptor ->
                        size = pfd.statSize
                        FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                            hash = OpenSubtitlesHash.compute(size) { off, buf ->
                                ch.read(ByteBuffer.wrap(buf), off)
                            }
                        }
                    }
                }
            }
            SubtitleQuery(
                title = parsed.title, season = parsed.season, episode = parsed.episode, year = parsed.year,
                languages = SubtitleLanguages.parseList(languageSetting), fileName = displayName,
                movieHash = hash, byteSize = if (hash != null) size else 0L,
            )
        }
}
