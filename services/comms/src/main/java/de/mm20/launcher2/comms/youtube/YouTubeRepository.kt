package de.mm20.launcher2.comms.youtube

import de.mm20.launcher2.comms.youtube.flow.AudioStreamSelector
import de.mm20.launcher2.comms.youtube.flow.NewPipeDownloader
import de.mm20.launcher2.comms.youtube.flow.YouTubeLink
import de.mm20.launcher2.comms.youtube.flow.parseYouTubeLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

data class YouTubeEntry(val url: String, val title: String, val artist: String, val thumbnail: String?, val durationSeconds: Long)
data class YouTubePlayback(val entry: YouTubeEntry, val streamUrl: String, val audioUrl: String? = null, val audioOnly: Boolean)

/** Extraction is lazy and always runs away from the main thread. No account is required. */

    class YouTubeRepository {
    private fun initialize() = synchronized(INIT_LOCK) {
        if (!initialized) {
            val locale = Locale.getDefault()
            NewPipe.init(NewPipeDownloader(), Localization(locale.language, locale.country), ContentCountry(locale.country.ifBlank { "GR" }))
            initialized = true
        }
    }
    suspend fun artistRadio(entry: YouTubeEntry): List<YouTubeEntry> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val query = "${entry.artist.ifBlank { entry.title }} radio mix"
        search(query, true)
    }

    suspend fun genreRadio(genre: String): List<YouTubeEntry> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        search("$genre music radio", true)
    }

    suspend fun search(query: String, music: Boolean): List<YouTubeEntry> = withContext(Dispatchers.IO) {
        initialize()
        val link = parseYouTubeLink(query)
        val videoId = when (link) { is YouTubeLink.Video -> link.id; is YouTubeLink.Short -> link.id; else -> null }
        if (videoId != null) return@withContext listOf(entry(StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")))
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query, if (music) listOf("music_songs") else listOf("videos"), "")
        SearchInfo.getInfo(service, handler).relatedItems.filterIsInstance<StreamInfoItem>().map {
            YouTubeEntry(it.url, it.name, it.uploaderName.orEmpty(), it.thumbnails.lastOrNull()?.url, it.duration)
        }
    }
    suspend fun resolve(item: YouTubeEntry, audioOnly: Boolean): YouTubePlayback = withContext(Dispatchers.IO) {
        initialize()
        val info = StreamInfo.getInfo(ServiceList.YouTube, item.url)
        val audio = AudioStreamSelector.selectPreferredAudioStream(info.audioStreams.filter { it.isUrl }, "original")
        val selected = entry(info)
        if (audioOnly) {
            val url = audio?.content ?: info.hlsUrl.takeIf { !it.isNullOrBlank() }
                ?: throw IllegalStateException("No playable audio stream")
            return@withContext YouTubePlayback(selected, url, audioOnly = true)
        }
        // Prefer combined streams; pair separate video/audio when only adaptive streams are available.
        val combined = info.videoStreams.filter { it.isUrl }.maxByOrNull { it.height }
        if (combined != null) return@withContext YouTubePlayback(selected, combined.content, audioOnly = false)
        val video = info.videoOnlyStreams.filter { it.isUrl && it.height <= 1080 }.maxByOrNull { it.height }
        if (video != null && audio != null) return@withContext YouTubePlayback(selected, video.content, audio.content, false)
        val hls = info.hlsUrl.takeIf { !it.isNullOrBlank() } ?: throw IllegalStateException("No playable video stream")
        YouTubePlayback(selected, hls, audioOnly = false)
    }
    private fun entry(info: StreamInfo) = YouTubeEntry(info.url, info.name, info.uploaderName.orEmpty(), info.thumbnails.lastOrNull()?.url, info.duration)
    private companion object { val INIT_LOCK = Any(); var initialized = false }
}
