package de.mm20.launcher2.ui.media.photos

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerInput
import de.mm20.launcher2.ui.component.rememberVirtualAppBlur
import de.mm20.launcher2.ui.component.virtualAppElevated
import de.mm20.launcher2.ui.component.virtualAppGlass
import de.mm20.launcher2.ui.component.virtualAppSurface
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.share.ShareActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.media.windowAround
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object PhotosRoute : NavKey

data class PhotoItem(
    val uri: Uri,
    val name: String,
    val folder: String,
    val taken: Long,
    val mime: String = "image/*",
) {
    val isVideo: Boolean get() = mime.startsWith("video/")
}

class PhotosViewModel : ViewModel() {
    private val _items = MutableStateFlow<List<PhotoItem>>(emptyList())
    val items: StateFlow<List<PhotoItem>> = _items

    fun load(context: Context) {
        viewModelScope.launch {
            _items.value = withContext(Dispatchers.IO) { query(context) }
        }
    }

    private fun query(context: Context): List<PhotoItem> {
        val out = ArrayList<PhotoItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                sel,
                args,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val mime = c.getString(5).orEmpty()
                    val mediaType = c.getInt(6)
                    val base = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val taken = c.getLong(3).takeIf { it > 0 } ?: (c.getLong(4) * 1000)
                    out += PhotoItem(
                        uri = ContentUris.withAppendedId(base, c.getLong(0)),
                        name = c.getString(1).orEmpty(),
                        folder = c.getString(2).orEmpty(),
                        taken = taken,
                        mime = mime.ifBlank { if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) "video/*" else "image/*" },
                    )
                }
            }
        }
        return out
    }
}

internal fun openViewer(context: Context, fullList: List<PhotoItem>, fullIndex: Int) {
    // thousands of pictures do not fit through an Intent (Binder limit): the viewer gets the ones around the chosen picture
    val (list, index) = fullList.windowAround(fullIndex)
    context.startActivity(
        Intent(context, PhotoViewerActivity::class.java).apply {
            putStringArrayListExtra(PhotoViewerActivity.EXTRA_URIS, ArrayList(list.map { it.uri.toString() }))
            putExtra(PhotoViewerActivity.EXTRA_INDEX, index)
        }
    )
}

private data class Album(val name: String, val items: List<PhotoItem>)

private fun dayLabel(context: Context, millis: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    val dayDiff = now.get(java.util.Calendar.DAY_OF_YEAR) - then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> context.getString(R.string.hc_today)
        sameYear && dayDiff == 1 -> context.getString(R.string.au_viewer_yesterday)
        else -> java.text.SimpleDateFormat(if (sameYear) "EEE, d MMM" else "d MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }
}

private fun monthLabel(millis: Long): String =
    java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(millis))

@Composable
private fun Thumb(item: PhotoItem, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = coil.request.ImageRequest.Builder(context).data(item.uri).size(320).crossfade(true).build(),
        contentDescription = item.name,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
/** The photo library. With [embedded] it sits inside Telos Viewer, which draws the status bar space and the tabs. */
fun PhotosScreen(embedded: Boolean = false) {
    val viewModel: PhotosViewModel = viewModel()
    // the guard of the whole app is set by Telos Viewer
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by viewModel.items.collectAsStateWithLifecycle()

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE
    val videoPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else permission
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        hasPermission = granted.values.any { it }
        if (hasPermission) viewModel.load(context)
    }
    LaunchedEffect(Unit) { if (hasPermission) viewModel.load(context) }

    var tab by de.mm20.launcher2.ui.component.rememberPersistentUiState("photos.tab", 0)
    var album by remember { mutableStateOf<Album?>(null) }
    var columns by rememberSaveable { mutableIntStateOf(4) }
    var selected by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    val selecting = selected.isNotEmpty()
    androidx.activity.compose.BackHandler(enabled = album != null || selecting) {
        if (selecting) selected = emptySet() else album = null
    }
    val blur = rememberVirtualAppBlur()
    val bar = virtualAppElevated()

    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val filteredItems = remember(items, query) {
        de.mm20.launcher2.comms.search.TelosSearch.filter(items, query) {
            listOf(
                it.name, it.folder, dayLabel(context, it.taken),
                java.text.SimpleDateFormat("d MMMM yyyy MMMM yyyy M/d/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it.taken)),
            )
        }
    }
    val albums = remember(filteredItems) {
        filteredItems.groupBy { it.folder }.map { (name, list) -> Album(name.ifBlank { context.getString(R.string.au_viewer_other) }, list) }
            .sortedByDescending { it.items.size }
    }
    val shown = remember(album, filteredItems) {
        val a = album
        if (a != null) { val keep = filteredItems.toSet(); a.items.filter { it in keep } } else filteredItems
    }
    val byMonth = remember(shown) { shown.groupBy { monthLabel(it.taken) } }
    val favUris = remember(shown) { ViewerLibrary.favorites(context) }
    val stories = remember(shown, favUris) {
        listOfNotNull(
            shown.take(12).takeIf { it.isNotEmpty() }?.let { Album(context.getString(R.string.viewer_story_recents), it) },
            shown.filter { it.folder.contains("screenshot", true) }.takeIf { it.isNotEmpty() }
                ?.let { Album(context.getString(R.string.viewer_story_screenshots), it) },
            shown.filter { it.uri.toString() in favUris }.takeIf { it.isNotEmpty() }
                ?.let { Album(context.getString(R.string.viewer_story_favorites), it) },
        )
    }

    androidx.compose.material3.Scaffold(
        containerColor = virtualAppSurface(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        bottomBar = {
            if (selecting) {
                NavigationBar(modifier = Modifier.virtualAppGlass(blur, bar), containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            val selectedUris = items.asSequence()
                                .filter { it.uri in selected }
                                .map { it.uri }
                                .toList()
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    ShareActions.shareUris(context, selectedUris)
                                }
                                if (!ok) {
                                    Toast.makeText(context, R.string.au20_viewer_share_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        icon = { Icon(painterResource(R.drawable.share_24px), contentDescription = null) },
                        label = { Text(stringResource(R.string.hc_share)) },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { selected = emptySet() },
                        icon = { Icon(painterResource(R.drawable.close_24px), contentDescription = null) },
                        label = { Text(stringResource(R.string.hc_cancel)) },
                    )
                }
            } else if (album == null) {
                NavigationBar(modifier = Modifier.virtualAppGlass(blur, bar), containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(painterResource(R.drawable.photo_24px), contentDescription = null) },
                        label = { Text(stringResource(R.string.hc_photos)) },
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(painterResource(R.drawable.crop_square_24px), contentDescription = null) },
                        label = { Text(stringResource(R.string.hc_albums)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().then(if (embedded) Modifier else Modifier.statusBarsPadding()).padding(start = 20.dp, end = 12.dp, top = if (embedded) 4.dp else 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (album != null) {
                    IconButton(onClick = { album = null }) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.hc_back))
                    }
                }
                Column {
                    if (!embedded || album != null) Text(
                        album?.name ?: stringResource(R.string.hc_photos),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.au_viewer_items, shown.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (hasPermission && items.isNotEmpty()) {
                de.mm20.launcher2.ui.media.MediaSearchBar(query, { query = it }, stringResource(R.string.tsm_search_photos))
            }
            if (hasPermission && query.isNotBlank() && shown.isEmpty()) {
                de.mm20.launcher2.ui.component.SearchEmptyState(query)
            } else if (!hasPermission) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.hc_allow_access_to_your_photos), style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { launcher.launch(arrayOf(permission, videoPermission).distinct().toTypedArray()) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text(stringResource(R.string.hc_allow)) }
                }
            } else if (tab == 1 && album == null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(albums.size) { i ->
                        val a = albums[i]
                        Column(Modifier.clickable { album = a }) {
                            Thumb(
                                a.items.first(),
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)),
                            )
                            Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                            Text(
                                "${a.items.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            } else {
                if (album == null && stories.isNotEmpty() && tab == 0) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(stories, key = { it.name }) { s ->
                            Column(Modifier.width(88.dp).clickable { album = s }) {
                                Thumb(
                                    s.items.first(),
                                    Modifier.size(88.dp).clip(RoundedCornerShape(20.dp)),
                                )
                                Text(
                                    s.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize().pointerInput(columns) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom > 1.08f) columns = (columns - 1).coerceIn(3, 5)
                            else if (zoom < 0.92f) columns = (columns + 1).coerceIn(3, 5)
                        }
                    },
                ) {
                    byMonth.forEach { (month, list) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                month,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
                            )
                        }
                        items(list.size) { i ->
                            val item = list[i]
                            val on = item.uri in selected
                            Box {
                                Thumb(
                                    item,
                                    Modifier.aspectRatio(1f).combinedClickable(
                                        onLongClick = {
                                            selected = if (on) selected - item.uri else selected + item.uri
                                        },
                                        onClick = {
                                            if (selecting) {
                                                selected = if (on) selected - item.uri else selected + item.uri
                                            } else openViewer(context, shown, shown.indexOf(item))
                                        },
                                    ),
                                )
                                if (item.isVideo) {
                                    Icon(
                                        painterResource(R.drawable.play_arrow_24px),
                                        contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                                    )
                                }
                                if (on) {
                                    Box(
                                        Modifier.matchParentSize().background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
