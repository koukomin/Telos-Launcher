package de.mm20.launcher2.ui.media.photos

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

internal fun openViewer(context: Context, list: List<PhotoItem>, index: Int) {
    context.startActivity(
        Intent(context, PhotoViewerActivity::class.java).apply {
            putStringArrayListExtra(PhotoViewerActivity.EXTRA_URIS, ArrayList(list.map { it.uri.toString() }))
            putExtra(PhotoViewerActivity.EXTRA_INDEX, index)
        }
    )
}

@Composable
fun PhotosScreen() {
    val viewModel: PhotosViewModel = viewModel()
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

    var folder by remember { mutableStateOf<String?>(null) }
    val folders = remember(items) { items.map { it.folder }.distinct().sorted() }
    val shown = remember(items, folder) { if (folder == null) items else items.filter { it.folder == folder } }

    if (!hasPermission) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Allow access to your photos", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { launcher.launch(permission) }, modifier = Modifier.padding(top = 16.dp)) { Text("Allow") }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(selected = folder == null, onClick = { folder = null }, label = { Text("All") }) }
            items(folders.size) { i ->
                FilterChip(
                    selected = folder == folders[i],
                    onClick = { folder = folders[i] },
                    label = { Text(folders[i]) },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(shown.size) { i ->
                AsyncImage(
                    model = shown[i].uri,
                    contentDescription = shown[i].name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(1f).clickable { openViewer(context, shown, i) },
                )
            }
        }
    }
}
