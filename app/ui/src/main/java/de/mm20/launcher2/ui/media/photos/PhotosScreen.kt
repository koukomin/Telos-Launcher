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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import de.mm20.launcher2.ui.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
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

data class PhotoItem(val uri: Uri, val name: String, val folder: String, val taken: Long)

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
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val taken = c.getLong(3).takeIf { it > 0 } ?: (c.getLong(4) * 1000)
                    out += PhotoItem(
                        uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)),
                        name = c.getString(1).orEmpty(),
                        folder = c.getString(2).orEmpty(),
                        taken = taken,
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

@Composable
fun PhotosScreen() {
    val viewModel: PhotosViewModel = viewModel()
    de.mm20.launcher2.ui.media.VirtualAppGuardEffect("telos_photos_app://photos")
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        if (it) viewModel.load(context)
    }
    LaunchedEffect(Unit) { if (hasPermission) viewModel.load(context) }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var album by remember { mutableStateOf<Album?>(null) }
    androidx.activity.compose.BackHandler(enabled = album != null) { album = null }

    var query by remember { mutableStateOf("") }
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
    val byDay = remember(shown) { shown.groupBy { dayLabel(context, it.taken) } }

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        bottomBar = {
            if (album == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (album != null) {
                    IconButton(onClick = { album = null }) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.hc_back))
                    }
                }
                Column {
                    Text(
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
                de.mm20.launcher2.ui.component.TelosSearchBar(query, { query = it }, stringResource(R.string.tsm_search_photos))
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
                    Button(onClick = { launcher.launch(permission) }, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.hc_allow)) }
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    byDay.forEach { (day, list) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                day,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
                            )
                        }
                        items(list.size) { i ->
                            Thumb(
                                list[i],
                                Modifier.aspectRatio(1f).clickable { openViewer(context, shown, shown.indexOf(list[i])) },
                            )
                        }
                    }
                }
            }
        }
    }
}
