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
internal val traktVersion = androidx.compose.runtime.mutableIntStateOf(0)

/** Raised when watched marks or the list of videos changed, so the rows draw again */
internal val libraryVersion = androidx.compose.runtime.mutableIntStateOf(0)

internal class VideoActions(val onDelete: (VideoItem) -> Unit, val onShare: (VideoItem) -> Unit)

internal val LocalVideoActions = androidx.compose.runtime.staticCompositionLocalOf<VideoActions?> { null }

internal data class VideoGroup(
    val title: String,
    val subtitle: String,
    val items: List<VideoItem>,
    val series: Boolean = false,
    val year: Int? = null,
)

internal fun metaKey(g: VideoGroup) = (if (g.series) "tv:" else "movie:") + g.title.lowercase() + ":" + g.year

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
    var confirmShare by remember { mutableStateOf<VideoItem?>(null) }
    val shareScope = androidx.compose.runtime.rememberCoroutineScope()
    fun performShare(video: VideoItem) {
        val uri = video.uri
        when (uri.scheme?.lowercase()) {
            "http", "https", "magnet" -> {
                if (!de.mm20.launcher2.ui.common.share.ShareActions.shareText(context, uri.toString(), video.title)) {
                    toast(context, context.getString(R.string.au10_video_share_failed))
                }
            }
            // a video on a network storage: its address is private, so only a public link could be shared
            "rem" -> toast(context, context.getString(R.string.au10_video_share_remote_private))
            else -> shareScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    val name = video.fileName.ifBlank { video.title }
                    val mime = de.mm20.launcher2.ui.common.share.ShareActions.mimeOf(name).takeIf { it.startsWith("video/") } ?: "video/*"
                    if (uri.scheme == "file") {
                        de.mm20.launcher2.ui.common.share.ShareActions.shareFile(context, java.io.File(uri.path.orEmpty()), mime, name)
                    } else {
                        de.mm20.launcher2.ui.common.share.ShareActions.shareFile(context, uri, mime, name)
                    }
                }
                if (!ok) toast(context, context.getString(R.string.au10_video_share_failed))
            }
        }
    }
    val videoActions = remember {
        VideoActions(
            onDelete = { confirmDelete = it },
            // big local videos are copied into the cache first: ask before
            onShare = { v ->
                if (v.sizeBytes > 50L * 1024 * 1024 && v.uri.scheme?.lowercase() in listOf("content", "file")) confirmShare = v
                else performShare(v)
            },
        )
    }
    confirmShare?.let { video ->
        AlertDialog(
            onDismissRequest = { confirmShare = null },
            title = { Text(stringResource(R.string.au10_video_share_large_title)) },
            text = { Text(stringResource(R.string.au10_video_share_large_message, de.mm20.launcher2.ui.files.formatSize(video.sizeBytes))) },
            confirmButton = { TextButton(onClick = { confirmShare = null; performShare(video) }) { Text(stringResource(R.string.au10_video_share_large_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmShare = null }) { Text(stringResource(R.string.hc_cancel)) } },
        )
    }
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
        FilledTonalIconButton(onClick = { showOpen = true }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))) {
            Icon(painterResource(R.drawable.link_24px), contentDescription = stringResource(R.string.hc_play_from_the_web))
        }
        FilledTonalIconButton(onClick = { showOrganize = true }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))) {
            Icon(painterResource(R.drawable.folder_24px), contentDescription = stringResource(R.string.au7_vidfolders_action))
        }
        FilledTonalIconButton(onClick = { showServices = true }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))) {
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
            DetailPage(current, shown, meta, traktConnected, onBack = { group = null }) { index -> openPlayer(context, shown, index) }
            return@Column
        }

        PillTabs(
            titles = listOf(R.string.au_video_tab_library, R.string.au_video_tab_movies, R.string.au_video_tab_series, R.string.au7_vidfolders_tab_other, R.string.au_video_tab_folders).map { stringResource(it) },
            selected = tab,
            onSelect = { tab = it },
        )
        when {
            loading -> VideoSkeleton()
            items.isEmpty() -> VideoEmptyState(R.drawable.movie_24px, stringResource(R.string.au14_videoui_empty_title), stringResource(R.string.hc_no_videos_found_on_this_device))
            filtered.isEmpty() && query.isNotBlank() -> de.mm20.launcher2.ui.component.SearchEmptyState(query)
            tab == 0 && query.isBlank() -> TintedHeader(null, Modifier.fillMaxSize()) {
                VideoHome(items, continueWatching, movies, series, others, folders, metas, showContinue = true) { group = it }
            }
            tab == 0 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.uri.toString() }) { video ->
                    VideoRow(video) { openPlayer(context, filtered, filtered.indexOf(video)) }
                }
            }
            tab == 1 -> PosterGrid(movies, metas, stringResource(R.string.au_video_no_movies)) { group = it }
            tab == 2 -> PosterGrid(series, metas, stringResource(R.string.au_video_no_series)) { group = it }
            tab == 3 -> if (others.isEmpty()) {
                VideoEmptyState(R.drawable.folder_24px, stringResource(R.string.au7_vidfolders_no_other), "")
            } else VideoList(others) { index -> openPlayer(context, others, index) }
            else -> GroupList(folders, "") { group = it }
        }
    }
    }
    }
}

