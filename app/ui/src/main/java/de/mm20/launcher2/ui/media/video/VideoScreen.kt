package de.mm20.launcher2.ui.media.video

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.media.video.EpisodeParser
import de.mm20.launcher2.comms.media.video.ResumeStore
import de.mm20.launcher2.comms.media.video.VideoItem
import de.mm20.launcher2.comms.search.GreekText
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object VideoRoute : NavKey

private data class VideoGroup(
    val title: String,
    val subtitle: String,
    val items: List<VideoItem>,
    val series: Boolean = false,
    val year: Int? = null,
)

private fun metaKey(g: VideoGroup) = (if (g.series) "tv:" else "movie:") + g.title.lowercase() + ":" + g.year

internal fun openPlayer(context: Context, list: List<VideoItem>, index: Int) {
    context.startActivity(
        Intent(context, VideoPlayerActivity::class.java).apply {
            putStringArrayListExtra(VideoPlayerActivity.EXTRA_URIS, ArrayList(list.map { it.uri.toString() }))
            putStringArrayListExtra(VideoPlayerActivity.EXTRA_TITLES, ArrayList(list.map { it.title }))
            putExtra(VideoPlayerActivity.EXTRA_INDEX, index)
        }
    )
}

@Composable
fun VideoScreen() {
    val viewModel: VideoViewModel = viewModel()
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.load(context)
    }
    LaunchedEffect(Unit) { if (hasPermission) viewModel.load(context) }

    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<VideoGroup?>(null) }
    BackHandler(enabled = group != null) { group = null }

    val filtered = remember(items, query) {
        if (query.isBlank()) items
        else {
            val q = GreekText.fold(query)
            items.filter { GreekText.fold(it.title).contains(q) }
        }
    }
    val resumeUris = remember(items) { ResumeStore.continueWatching(context).toSet() }
    val continueWatching = remember(items, resumeUris) {
        ResumeStore.continueWatching(context).mapNotNull { uri -> items.firstOrNull { it.uri.toString() == uri } }.take(10)
    }
    val series = remember(filtered) {
        filtered.mapNotNull { item ->
            val parsed = EpisodeParser.parse(item.fileName)
            if (parsed.isEpisode) Triple(parsed.title.lowercase(), parsed, item) else null
        }.groupBy { it.first }.values.map { list ->
            val name = list.first().second.title
            val episodes = list.sortedWith(compareBy({ it.second.season }, { it.second.episode })).map { it.third }
            VideoGroup(name, "${episodes.size} episodes", episodes, series = true)
        }.sortedBy { it.title.lowercase() }
    }
    val movies = remember(filtered) {
        filtered.mapNotNull { item ->
            val parsed = EpisodeParser.parse(item.fileName)
            if (parsed.isEpisode) null else Triple(parsed.title.lowercase() + "|" + parsed.year, parsed, item)
        }.groupBy { it.first }.values.map { list ->
            val parsed = list.first().second
            VideoGroup(parsed.title, parsed.year?.toString() ?: "${list.size} file(s)", list.map { it.third }, series = false, year = parsed.year)
        }.sortedBy { it.title.lowercase() }
    }
    var showOpen by remember { mutableStateOf(false) }
    var showServices by remember { mutableStateOf(false) }
    var servicesVersion by remember { mutableStateOf(0) }
    val services by produceState<de.mm20.launcher2.comms.media.video.VideoServicesConfig?>(null, servicesVersion) {
        value = de.mm20.launcher2.comms.media.video.VideoServices.config()
    }
    val metas = remember { mutableStateMapOf<String, de.mm20.launcher2.comms.media.video.VideoMeta?>() }
    LaunchedEffect(services, series, movies) {
        val cfg = services ?: return@LaunchedEffect
        if (!cfg.postersEnabled) return@LaunchedEffect
        val language = cfg.languages.substringBefore(',').ifBlank { "en" }
        for (g in series + movies) {
            val k = metaKey(g)
            if (metas.containsKey(k)) continue
            val vm = de.mm20.launcher2.comms.media.video.VideoMetadata
            val tmdb = cfg.tmdbKey.isNotBlank()
            if (vm.known(context, g.series, g.title, g.year, tmdb)) {
                metas[k] = vm.cached(context, g.series, g.title, g.year, tmdb)
                continue
            }
            metas[k] = vm.lookup(context, cfg.tmdbKey, g.series, g.title, g.year, language)
            kotlinx.coroutines.delay(150)
        }
    }
    val folders = remember(filtered) {
        filtered.groupBy { it.folder }
            .map { (name, list) -> VideoGroup(name, "${list.size} videos", list.sortedBy { it.title.lowercase() }) }
            .sortedBy { it.title.lowercase() }
    }

    de.mm20.launcher2.ui.media.MediaFrame("Videos", actions = {
        IconButton(onClick = { showOpen = true }) {
            Icon(painterResource(R.drawable.link_24px), contentDescription = "Play from the web")
        }
        IconButton(onClick = { showServices = true }) {
            Icon(painterResource(R.drawable.settings_24px), contentDescription = "Video services")
        }
    }) {
    if (showOpen) OpenSourceDialog { showOpen = false }
    if (showServices) VideoServicesDialog { showServices = false; servicesVersion++ }
    Column(Modifier.fillMaxSize()) {
        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Allow access to your videos to build the library", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { permissionLauncher.launch(permission) }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Allow")
                }
            }
            return@Column
        }

        de.mm20.launcher2.ui.media.MediaSearchBar(query, { query = it }, "Search videos")

        val current = group
        if (current != null) {
            val meta = metas[metaKey(current)]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = { group = null }) {
                    Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "Back")
                }
                if (meta?.posterUrl != null) {
                    Poster(meta.posterUrl, Modifier.width(60.dp).height(90.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(meta?.title?.ifBlank { null } ?: current.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(meta?.year?.ifBlank { null }, meta?.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) }, current.subtitle).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!meta?.overview.isNullOrBlank()) {
                        Text(meta!!.overview, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            VideoList(current.items) { index -> openPlayer(context, current.items, index) }
            return@Column
        }

        TabRow(selectedTabIndex = tab) {
            listOf("Library", "Movies", "Series", "Folders").forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No videos found on this device", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            tab == 0 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                if (continueWatching.isNotEmpty() && query.isBlank()) {
                    item {
                        Text("Continue watching", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp))
                    }
                    items(continueWatching, key = { "c-" + it.id }) { video ->
                        VideoRow(video) { openPlayer(context, continueWatching, continueWatching.indexOf(video)) }
                    }
                    item {
                        Text("All videos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
                    }
                }
                items(filtered, key = { it.id }) { video ->
                    VideoRow(video) { openPlayer(context, filtered, filtered.indexOf(video)) }
                }
            }
            tab == 1 -> PosterGrid(movies, metas, "No movies found") { group = it }
            tab == 2 -> PosterGrid(series, metas, "No series recognised. Name files like Show.S01E02.mkv") { group = it }
            else -> GroupList(folders, "") { group = it }
        }
    }
    }
}

@Composable
private fun GroupList(groups: List<VideoGroup>, emptyText: String, onOpen: (VideoGroup) -> Unit) {
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        items(groups, key = { it.title }) { g ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(g) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoThumb(g.items.first(), Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(g.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(g.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VideoList(list: List<VideoItem>, onPlay: (Int) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        items(list.size, key = { list[it].id }) { index -> VideoRow(list[index]) { onPlay(index) } }
    }
}

@Composable
private fun VideoRow(video: VideoItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val progress = remember(video.uri) { ResumeStore.progress(context, video.uri) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            VideoThumb(video, Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(8.dp)))
            if (progress > 0.02f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(video.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                formatDuration(video.durationMs) + " · " + video.folder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VideoThumb(video: VideoItem, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, video.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(video.uri, Size(320, 180), null)
                else null
            }.getOrNull()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b == null) {
            Icon(painterResource(R.drawable.play_circle_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        } else {
            Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
internal fun Poster(url: String?, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (url == null) {
            Icon(painterResource(R.drawable.movie_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        } else {
            coil.compose.AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PosterGrid(
    groups: List<VideoGroup>,
    metas: Map<String, de.mm20.launcher2.comms.media.video.VideoMeta?>,
    emptyText: String,
    onOpen: (VideoGroup) -> Unit,
) {
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(groups.size) { i ->
            val g = groups[i]
            val meta = metas[metaKey(g)]
            Column(Modifier.clickable { onOpen(g) }) {
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
                Text(g.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
