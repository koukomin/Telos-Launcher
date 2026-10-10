package de.mm20.launcher2.ui.media.video

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import de.mm20.launcher2.comms.media.windowAround
import android.os.Build
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.media.video.EpisodeParser
import de.mm20.launcher2.comms.media.video.ResumeStore
import de.mm20.launcher2.comms.media.video.VideoItem
import de.mm20.launcher2.comms.search.GreekText
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object VideoRoute : NavKey

/** Raised when the watched marks from Trakt were refreshed, so the rows draw again */
private val traktVersion = androidx.compose.runtime.mutableIntStateOf(0)

/** Raised when watched marks or the list of videos changed, so the rows draw again */
private val libraryVersion = androidx.compose.runtime.mutableIntStateOf(0)

private class VideoActions(val onDelete: (VideoItem) -> Unit)

private val LocalVideoActions = androidx.compose.runtime.staticCompositionLocalOf<VideoActions?> { null }

private data class VideoGroup(
    val title: String,
    val subtitle: String,
    val items: List<VideoItem>,
    val series: Boolean = false,
    val year: Int? = null,
)

private fun metaKey(g: VideoGroup) = (if (g.series) "tv:" else "movie:") + g.title.lowercase() + ":" + g.year

internal fun openPlayer(context: Context, fullList: List<VideoItem>, fullIndex: Int) {
    // a whole library does not fit through an Intent (Binder limit): the player gets the videos around the chosen one
    val (list, index) = fullList.windowAround(fullIndex)
    context.startActivity(
        Intent(context, PlayerChoice.playerClass(context)).apply {
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
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) viewModel.load(context)
    }
    LaunchedEffect(Unit) { if (hasPermission) viewModel.load(context) }
    // back from the player (new watch positions) or from the system settings (permission changed there)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        libraryVersion.intValue++
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted != hasPermission) {
            hasPermission = granted
            if (granted) viewModel.load(context)
        }
    }
    // the Trakt login is decrypted with the keystore: not on the main thread
    val traktConnected by produceState(false) {
        value = withContext(Dispatchers.IO) {
            runCatching { de.mm20.launcher2.comms.media.video.trakt.Trakt.login(context).connected }.getOrDefault(false)
        }
    }

    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by remember { mutableStateOf<VideoGroup?>(null) }
    BackHandler(enabled = group != null) { group = null }

    val filtered = remember(items, query) {
        de.mm20.launcher2.comms.search.TelosSearch.filter(items, query) { listOf(it.title, it.fileName, it.folder) }
    }
    val continueWatching = remember(items, libraryVersion.intValue) {
        ResumeStore.continueWatching(context).mapNotNull { uri -> items.firstOrNull { it.uri.toString() == uri } }.take(10)
    }
    // the file names are parsed once per library, not on every letter typed in the search field
    val parsedNames = remember(items) { items.associate { it.id to EpisodeParser.parse(it.fileName) } }
    val series = remember(filtered, parsedNames) {
        filtered.mapNotNull { item ->
            val parsed = parsedNames[item.id] ?: EpisodeParser.parse(item.fileName)
            if (parsed.isEpisode) Triple(parsed.title.lowercase(), parsed, item) else null
        }.groupBy { it.first }.values.map { list ->
            val name = list.first().second.title
            val episodes = list.sortedWith(compareBy({ it.second.season }, { it.second.episode })).map { it.third }
            VideoGroup(name, context.getString(R.string.au_video_episodes_count, episodes.size), episodes, series = true)
        }.sortedBy { it.title.lowercase() }
    }
    val movies = remember(filtered, parsedNames) {
        filtered.mapNotNull { item ->
            val parsed = parsedNames[item.id] ?: EpisodeParser.parse(item.fileName)
            if (parsed.isEpisode || parsed.year == null) null else Triple(parsed.title.lowercase() + "|" + parsed.year, parsed, item)
        }.groupBy { it.first }.values.map { list ->
            val parsed = list.first().second
            VideoGroup(parsed.title, parsed.year?.toString() ?: context.getString(R.string.au_video_files_count, list.size), list.map { it.third }, series = false, year = parsed.year)
        }.sortedBy { it.title.lowercase() }
    }
    // neither an episode nor a film with a year in its name: not recognised
    val others = remember(filtered, parsedNames) {
        filtered.filter { item ->
            val parsed = parsedNames[item.id] ?: EpisodeParser.parse(item.fileName)
            !parsed.isEpisode && parsed.year == null
        }
    }
    var showOrganize by remember { mutableStateOf(false) }
    if (showOrganize) VideoOrganizeDialog(items) { showOrganize = false; if (it) viewModel.load(context) }
    LaunchedEffect(Unit) {
        de.mm20.launcher2.comms.media.video.trakt.Trakt.refreshWatched(context)
        runCatching { de.mm20.launcher2.comms.media.video.trakt.Trakt.flushQueue(context) }
        traktVersion.intValue++
    }
    var pendingDelete by remember { mutableStateOf<VideoItem?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingDelete?.let { ResumeStore.markUnwatched(context, it.uri) }
            viewModel.load(context)
        }
        pendingDelete = null
    }
    var confirmDelete by remember { mutableStateOf<VideoItem?>(null) }
    fun performDelete(video: VideoItem) {
        pendingDelete = video
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                val request = android.provider.MediaStore.createDeleteRequest(context.contentResolver, listOf(video.uri))
                deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                try {
                    context.contentResolver.delete(video.uri, null, null)
                    ResumeStore.markUnwatched(context, video.uri)
                    viewModel.load(context)
                } catch (e: SecurityException) {
                    val action = (e as? android.app.RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                    if (action != null) deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(action).build()) else throw e
                }
            }
        }.onFailure { toast(context, context.getString(R.string.vn_delete_failed)) }
    }
    val videoActions = remember { VideoActions { confirmDelete = it } }
    confirmDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.vn_delete_video)) },
            text = { Text(stringResource(R.string.vn_delete_confirm, video.fileName)) },
            confirmButton = { TextButton(onClick = { confirmDelete = null; performDelete(video) }) { Text(stringResource(R.string.vn_delete_video)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.hc_cancel)) } },
        )
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
        // the cache is a JSON file that is read for every title: off the main thread
        withContext(Dispatchers.IO) {
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
    }
    val otherFolder = stringResource(R.string.au_video_other_folder)
    val folders = remember(filtered, otherFolder) {
        filtered.groupBy { it.folder.ifBlank { otherFolder } }
            .map { (name, list) -> VideoGroup(name, context.getString(R.string.au_video_videos_count, list.size), list.sortedBy { it.title.lowercase() }) }
            .sortedBy { it.title.lowercase() }
    }

    de.mm20.launcher2.ui.media.MediaFrame(stringResource(R.string.au_video_title), guardKey = "telos_video_app://video", actions = {
        IconButton(onClick = { showOpen = true }) {
            Icon(painterResource(R.drawable.link_24px), contentDescription = stringResource(R.string.hc_play_from_the_web))
        }
        IconButton(onClick = { showOrganize = true }) {
            Icon(painterResource(R.drawable.folder_24px), contentDescription = stringResource(R.string.au7_vidfolders_action))
        }
        IconButton(onClick = { showServices = true }) {
            Icon(painterResource(R.drawable.settings_24px), contentDescription = stringResource(R.string.hc_video_services))
        }
    }) {
    if (showOpen) OpenSourceDialog { showOpen = false }
    if (showServices) VideoServicesDialog { showServices = false; servicesVersion++; viewModel.load(context) }
    androidx.compose.runtime.CompositionLocalProvider(LocalVideoActions provides videoActions) {
    Column(Modifier.fillMaxSize()) {
        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.hc_allow_access_to_your_videos_to_build_the), style = MaterialTheme.typography.titleMedium)
                Button(onClick = { permissionLauncher.launch(permission) }, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.hc_allow))
                }
                if (permissionDenied) {
                    // after two refusals the system no longer shows its dialog: the switch is in the app settings
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text(stringResource(R.string.au_video_open_settings)) }
                }
            }
            return@Column
        }

        val current = group
        // inside a series or folder the search field would have no effect
        if (current == null) {
            de.mm20.launcher2.ui.media.MediaSearchBar(query, { query = it }, stringResource(R.string.tsm_search_videos))
        }
        if (current != null) {
            // videos deleted in the meantime drop out of the open group
            val present = remember(items) { items.mapTo(HashSet()) { it.id } }
            val shown = remember(current, present) { current.items.filter { it.id in present } }
            LaunchedEffect(shown.isEmpty()) { if (shown.isEmpty()) group = null }
            val meta = metas[metaKey(current)]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = { group = null }) {
                    Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.hc_back))
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
                    if (traktConnected) {
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        TextButton(onClick = {
                            scope.launch {
                                val ok = de.mm20.launcher2.comms.media.video.trakt.Trakt.addToWatchlist(context, current.title, current.year, current.series)
                                toast(context, context.getString(if (ok) R.string.au_video_watchlist_added else R.string.au_video_watchlist_failed))
                            }
                        }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.hc_add_to_trakt_watchlist)) }
                    }
                    if (!meta?.overview.isNullOrBlank()) {
                        Text(meta!!.overview, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (current.series) SeasonedVideoList(shown) { index -> openPlayer(context, shown, index) }
            else VideoList(shown) { index -> openPlayer(context, shown, index) }
            return@Column
        }

        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            listOf(R.string.au_video_tab_library, R.string.au_video_tab_movies, R.string.au_video_tab_series, R.string.au7_vidfolders_tab_other, R.string.au_video_tab_folders).forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(title)) })
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.hc_no_videos_found_on_this_device), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            filtered.isEmpty() && query.isNotBlank() -> de.mm20.launcher2.ui.component.SearchEmptyState(query)
            tab == 0 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                if (continueWatching.isNotEmpty() && query.isBlank()) {
                    item {
                        Text(stringResource(R.string.hc_continue_watching), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp))
                    }
                    items(continueWatching, key = { "c-" + it.uri }) { video ->
                        VideoRow(video) { openPlayer(context, continueWatching, continueWatching.indexOf(video)) }
                    }
                    item {
                        Text(stringResource(R.string.hc_all_videos), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
                    }
                }
                items(filtered, key = { it.uri.toString() }) { video ->
                    VideoRow(video) { openPlayer(context, filtered, filtered.indexOf(video)) }
                }
            }
            tab == 1 -> PosterGrid(movies, metas, stringResource(R.string.au_video_no_movies)) { group = it }
            tab == 2 -> PosterGrid(series, metas, stringResource(R.string.au_video_no_series)) { group = it }
            tab == 3 -> if (others.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.au7_vidfolders_no_other), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else VideoList(others) { index -> openPlayer(context, others, index) }
            else -> GroupList(folders, "") { group = it }
        }
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
        items(groups.size) { index ->
            val g = groups[index]
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
        items(list.size, key = { list[it].uri.toString() }) { index -> VideoRow(list[index]) { onPlay(index) } }
    }
}

/** The episodes of a series, under a heading per season */
@Composable
private fun SeasonedVideoList(list: List<VideoItem>, onPlay: (Int) -> Unit) {
    val seasons = remember(list) { list.map { EpisodeParser.parse(it.fileName).season } }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        list.indices.forEach { index ->
            if (index == 0 || seasons[index] != seasons[index - 1]) {
                item(key = "season-$index") {
                    Text(
                        stringResource(R.string.au7_vidfolders_season, seasons[index] ?: 0),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                    )
                }
            }
            item(key = list[index].uri.toString()) { VideoRow(list[index]) { onPlay(index) } }
        }
    }
}

@Composable
private fun VideoRow(video: VideoItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val progress = remember(video.uri, libraryVersion.intValue) { ResumeStore.progress(context, video.uri) }
    val actions = LocalVideoActions.current
    var menu by remember { mutableStateOf(false) }
    val seen = remember(video.uri, libraryVersion.intValue) { ResumeStore.isWatched(context, video.uri) }
    val watched = seen || remember(video.uri, traktVersion.intValue) {
        de.mm20.launcher2.comms.media.video.trakt.Trakt.isWatched(context, EpisodeParser.parse(video.fileName))
    }
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            Text((if (watched) "✓ " else "") + video.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (seen) R.string.vn_mark_unwatched else R.string.vn_mark_watched)) },
                    onClick = {
                        menu = false
                        if (seen) ResumeStore.markUnwatched(context, video.uri) else ResumeStore.markWatched(context, video.uri, video.durationMs)
                        libraryVersion.intValue++
                    },
                )
                if (video.id > 0) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vn_delete_video)) },
                        onClick = { menu = false; actions?.onDelete?.invoke(video) },
                    )
                }
            }
            Text(
                formatDuration(video.durationMs) + " · " + video.folder.ifBlank { stringResource(R.string.au_video_other_folder) },
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
