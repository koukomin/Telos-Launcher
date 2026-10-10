package de.mm20.launcher2.ui.media.video

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import de.mm20.launcher2.comms.media.video.ResumeStore
import de.mm20.launcher2.comms.media.video.VideoItem
import de.mm20.launcher2.comms.media.video.VideoMeta
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** The dominant colour of a poster, used to tint the header of the detail page. Cached per poster address. */
internal object PosterTint {
    private const val NONE = 0
    private val cache = LruCache<String, Int>(200)

    /** Null when the poster cannot be loaded or has no usable colour */
    suspend fun of(context: Context, url: String): Color? {
        cache.get(url)?.let { return if (it == NONE) null else Color(it) }
        val color = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context).data(url).size(48, 72).allowHardware(false).build()
                val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { dominant(it) }
            }.getOrNull()
        }
        cache.put(url, color ?: NONE)
        return color?.let { Color(it) }
    }

    /** Average of the colourful, not too dark and not too bright pixels, weighted by saturation */
    private fun dominant(bitmap: android.graphics.Bitmap): Int? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var total = 0.0
        val hsv = FloatArray(3)
        for (p in pixels) {
            android.graphics.Color.colorToHSV(p, hsv)
            if (hsv[2] < 0.15f || hsv[2] > 0.95f) continue
            val weight = (hsv[1] * hsv[1] + 0.02f).toDouble()
            r += android.graphics.Color.red(p) * weight
            g += android.graphics.Color.green(p) * weight
            b += android.graphics.Color.blue(p) * weight
            total += weight
        }
        if (total <= 0.0) return null
        return android.graphics.Color.rgb((r / total).toInt(), (g / total).toInt(), (b / total).toInt())
    }
}

internal enum class HomeChip { Movies, Series, Other, Recent, Unwatched, Folders }

/** A translucent gradient from [tint] at the top to nothing, behind a header. Animated unless animations are reduced. */
@Composable
internal fun TintedHeader(tint: Color?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduce by koinInject<PerformanceSettings>().reduceAnimations.collectAsState(false)
    val base = MaterialTheme.colorScheme.surface
    val target = (tint ?: MaterialTheme.colorScheme.primaryContainer).copy(alpha = if (tint != null) 0.55f else 0.25f)
    val color by animateColorAsState(target, if (reduce) snap() else tween(500), label = "videoTint")
    Box(modifier.background(Brush.verticalGradient(listOf(color, base.copy(alpha = 0f))))) { content() }
}

/** The home of Telos Video: category chips on top, shelves below (or the chosen category alone) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VideoHome(
    all: List<VideoItem>,
    continueWatching: List<VideoItem>,
    movies: List<VideoGroup>,
    series: List<VideoGroup>,
    others: List<VideoItem>,
    folders: List<VideoGroup>,
    metas: Map<String, VideoMeta?>,
    showContinue: Boolean,
    onOpenGroup: (VideoGroup) -> Unit,
) {
    val context = LocalContext.current
    val recent = remember(all) { all.sortedByDescending { it.dateAddedSeconds }.take(60) }
    val unwatched = remember(all, libraryVersion.intValue) { all.filter { ResumeStore.progress(context, it.uri) < 0.02f } }
    val chips = remember(movies, series, others, recent, unwatched, folders) {
        buildList {
            if (movies.isNotEmpty()) add(HomeChip.Movies)
            if (series.isNotEmpty()) add(HomeChip.Series)
            if (others.isNotEmpty()) add(HomeChip.Other)
            if (recent.isNotEmpty()) add(HomeChip.Recent)
            if (unwatched.isNotEmpty()) add(HomeChip.Unwatched)
            if (folders.isNotEmpty()) add(HomeChip.Folders)
        }
    }
    var selected by rememberSaveable { mutableStateOf<HomeChip?>(null) }
    val chip = selected?.takeIf { it in chips }
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips) { c ->
                FilterChip(
                    selected = chip == c,
                    onClick = { selected = if (chip == c) null else c },
                    label = {
                        Text(
                            stringResource(
                                when (c) {
                                    HomeChip.Movies -> R.string.au_video_tab_movies
                                    HomeChip.Series -> R.string.au_video_tab_series
                                    HomeChip.Other -> R.string.au7_vidfolders_tab_other
                                    HomeChip.Recent -> R.string.au10_video_chip_recent
                                    HomeChip.Unwatched -> R.string.au10_video_chip_unwatched
                                    HomeChip.Folders -> R.string.au_video_tab_folders
                                }
                            )
                        )
                    },
                )
            }
        }
        val empty = stringResource(R.string.au10_video_nothing_here)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (chip) {
                HomeChip.Movies -> PosterGrid(movies, metas, empty, onOpenGroup)
                HomeChip.Series -> PosterGrid(series, metas, empty, onOpenGroup)
                HomeChip.Other -> VideoList(others) { openPlayer(context, others, it) }
                HomeChip.Recent -> VideoList(recent) { openPlayer(context, recent, it) }
                HomeChip.Unwatched -> VideoList(unwatched) { openPlayer(context, unwatched, it) }
                HomeChip.Folders -> GroupList(folders, empty, onOpenGroup)
                null -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                    if (showContinue && continueWatching.isNotEmpty()) {
                        item(key = "h-continue") { ShelfTitle(stringResource(R.string.hc_continue_watching)) }
                        item(key = "s-continue") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(continueWatching, key = { it.uri.toString() }) { v ->
                                    WideCard(v) { openPlayer(context, continueWatching, continueWatching.indexOf(v)) }
                                }
                            }
                        }
                    }
                    if (recent.isNotEmpty()) {
                        val shelf = recent.take(20)
                        item(key = "h-recent") { ShelfTitle(stringResource(R.string.au10_video_recently_added)) }
                        item(key = "s-recent") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(shelf, key = { it.uri.toString() }) { v ->
                                    WideCard(v, width = 200) { openPlayer(context, shelf, shelf.indexOf(v)) }
                                }
                            }
                        }
                    }
                    if (movies.isNotEmpty()) {
                        item(key = "h-movies") { ShelfTitle(stringResource(R.string.au_video_tab_movies)) }
                        item(key = "s-movies") { PosterShelf(movies.take(30), metas, onOpenGroup) }
                    }
                    if (series.isNotEmpty()) {
                        item(key = "h-series") { ShelfTitle(stringResource(R.string.au_video_tab_series)) }
                        item(key = "s-series") { PosterShelf(series.take(30), metas, onOpenGroup) }
                    }
                    if (movies.isEmpty() && series.isEmpty() && recent.isEmpty()) {
                        item { Text(empty, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
}

@Composable
private fun PosterShelf(groups: List<VideoGroup>, metas: Map<String, VideoMeta?>, onOpen: (VideoGroup) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(groups, key = { metaKey(it) }) { g ->
            val meta = metas[metaKey(g)]
            Column(Modifier.width(120.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { onOpen(g) })) {
                if (meta?.posterUrl != null) {
                    Poster(meta.posterUrl, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
                } else {
                    VideoThumb(g.items.first(), Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
                }
                Text(
                    meta?.title?.ifBlank { null } ?: g.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** A 16:9 card with a progress bar and the time that is left; long press opens the video menu */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WideCard(video: VideoItem, width: Int = 260, onClick: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val progress = remember(video.uri, libraryVersion.intValue) { ResumeStore.progress(context, video.uri) }
    val seen = remember(video.uri, libraryVersion.intValue) { ResumeStore.isWatched(context, video.uri) }
    Column(Modifier.width(width.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onClick, onLongClick = { menu = true })) {
        Box {
            VideoThumb(video, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
            if (progress > 0.02f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                )
            }
            VideoMenu(video, menu, seen) { menu = false }
        }
        Text(
            video.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (progress in 0.02f..0.95f && video.durationMs > 0) {
            Text(
                stringResource(R.string.au10_video_remaining, formatDuration((video.durationMs * (1f - progress)).toLong())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
