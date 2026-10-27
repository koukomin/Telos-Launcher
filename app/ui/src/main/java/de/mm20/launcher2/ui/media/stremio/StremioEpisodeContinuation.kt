package de.mm20.launcher2.ui.media.stremio

import android.content.Context
import android.content.Intent
import de.mm20.launcher2.comms.stremio.*
import de.mm20.launcher2.ui.media.video.VideoPlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Resolve only a catalog-provided next episode, from the same add-on and transport. Never open a browser. */
internal object StremioEpisodeContinuation {
    suspend fun next(context: Context, current: Intent): Intent? = withTimeoutOrNull(15_000) {
        withContext(Dispatchers.IO) {
            val preferences = StremioPlaybackSettings.load(context)
            if (!preferences.autoNextEpisode) return@withContext null
            val ids = current.getStringArrayListExtra("stremio_next_ids").orEmpty()
            val titles = current.getStringArrayListExtra("stremio_next_titles").orEmpty()
            val nextId = ids.firstOrNull() ?: return@withContext null
            val type = current.getStringExtra("stremio_type") ?: return@withContext null
            val addonName = current.getStringExtra("stremio_addon") ?: return@withContext null
            val kind = current.getStringExtra("stremio_kind") ?: return@withContext null
            val client = StremioClient()
            val addons = StremioAddons(context.applicationContext, client).addons.value
            val streams = client.allStreams(addons.filter { it.name == addonName }, type, nextId)
            val stream = StremioStreamFilter.apply(streams, preferences, fallback = false).firstOrNull {
                it.kind.name == kind && (it.kind == StreamItem.Kind.Web ||
                    (it.kind == StreamItem.Kind.Torrent && (it.fileIdx ?: -1) >= 0))
            } ?: return@withContext null
            val title = titles.firstOrNull().orEmpty().ifBlank { nextId }
            val intent = Intent(current).apply {
                data = null
                putExtra("stremio_auto_continuation", true)
                removeExtra(VideoPlayerActivity.EXTRA_SOURCE)
                removeExtra(VideoPlayerActivity.EXTRA_FILE_IDX)
                removeExtra(VideoPlayerActivity.EXTRA_URIS)
                removeExtra(VideoPlayerActivity.EXTRA_TITLES)
                putExtra("stremio_video_id", nextId)
                putExtra("stremio_video_title", title)
                putExtra("stremio_resume_ms", 0L)
                putExtra("stremio_watched_ms", 0L)
                putExtra("stremio_stream_key", stream.streamKey().take(4096))
                putStringArrayListExtra("stremio_next_ids", ArrayList(ids.drop(1)))
                putStringArrayListExtra("stremio_next_titles", ArrayList(titles.drop(1)))
                putStringArrayListExtra(VideoPlayerActivity.EXTRA_TITLES, arrayListOf(title))
            }
            if (stream.kind == StreamItem.Kind.Web) {
                intent.putStringArrayListExtra(VideoPlayerActivity.EXTRA_URIS, arrayListOf(stream.url!!))
            } else {
                val magnet = StremioProtocol.magnet(stream, title) ?: return@withContext null
                intent.putExtra(VideoPlayerActivity.EXTRA_SOURCE, magnet)
                stream.fileIdx?.let { intent.putExtra(VideoPlayerActivity.EXTRA_FILE_IDX, it) }
            }
            val subtitles = (stream.subtitles + client.allSubtitles(addons, type, nextId)).distinctBy { it.url }.take(15)
            intent.putStringArrayListExtra(VideoPlayerActivity.EXTRA_SUB_URLS, ArrayList(subtitles.map { it.url }))
                .putStringArrayListExtra(VideoPlayerActivity.EXTRA_SUB_LANGS, ArrayList(subtitles.map { it.lang }))
                .putStringArrayListExtra(VideoPlayerActivity.EXTRA_SUB_LABELS, ArrayList(subtitles.map { it.label.orEmpty() }))
        }
    }
}
