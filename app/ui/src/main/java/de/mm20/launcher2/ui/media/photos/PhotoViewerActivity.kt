package de.mm20.launcher2.ui.media.photos

import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme

/** Full screen photo viewer with pinch zoom, EXIF editor and metadata-free sharing. */
class PhotoViewerActivity : BaseActivity() {

    override fun onStart() {
        super.onStart()
        de.mm20.launcher2.base.VirtualAppGuard.enter(this, "telos_photos_app://photos")
    }

    override fun onStop() {
        de.mm20.launcher2.base.VirtualAppGuard.leave(this, "telos_photos_app://photos")
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val uris = intent.getStringArrayListExtra(EXTRA_URIS)?.map { Uri.parse(it) }
            ?: intent.data?.let { listOf(it) }
            // a picture shared to Telos Viewer
            ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { listOf(it) }
        if (uris.isNullOrEmpty()) {
            finish()
            return
        }
        val start = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, uris.size - 1)
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    PhotoViewer(uris, start, onClose = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_URIS = "de.mm20.launcher2.photos.URIS"
        const val EXTRA_INDEX = "de.mm20.launcher2.photos.INDEX"
    }
}

@Composable
private fun ZoomableImage(uri: Uri, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) { scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero } else {
                            // zoom in around the tapped point
                            val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                            val maxX = size.width * 1.5f / 2f
                            val maxY = size.height * 1.5f / 2f
                            val raw = (tap - c) * (1f - 2.5f)
                            scale = 2.5f
                            offset = androidx.compose.ui.geometry.Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // Only a pinch, or a drag while zoomed in, is taken here. A single finger on a photo
                // that is not zoomed is left to the pager, so that swiping to the next photo works.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val newScale = (scale * zoom).coerceIn(1f, 8f)
                            val f = newScale / scale
                            // the layer scales around its centre: keep the point between the fingers where it is
                            val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                            scale = newScale
                            offset = if (scale > 1f) {
                                // the photo cannot be dragged out of the screen
                                val maxX = size.width * (scale - 1f) / 2f
                                val maxY = size.height * (scale - 1f) / 2f
                                val raw = (centroid - c) * (1f - f) + offset * f + pan
                                androidx.compose.ui.geometry.Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
                            } else androidx.compose.ui.geometry.Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
    )
}

@Composable
private fun PhotoViewer(uris: List<Uri>, start: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start) { uris.size }
    var chrome by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    val current = uris[pager.currentPage]

    // Action that needs write access; runs after the user granted it (API 30+) or immediately.
    var pendingWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) pendingWrite?.invoke()
        pendingWrite = null
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) onClose()
    }
    fun withWriteAccess(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 30 && current.authority == MediaStore.AUTHORITY) {
            pendingWrite = action
            val sender = MediaStore.createWriteRequest(context.contentResolver, listOf(current)).intentSender
            writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else action()
    }

    fun share(uri: Uri) {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, null
            )
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(uris[page]) { chrome = !chrome }
        }
        if (chrome) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x66000000)).statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.arrow_back_24px), "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${pager.currentPage + 1} / ${uris.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000))
                    .navigationBarsPadding().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ViewerAction(R.drawable.share_24px, "Share") { share(current) }
                ViewerAction(R.drawable.tune_24px, "Edit") {
                    context.startActivity(Intent(context, PhotoEditorActivity::class.java).setData(current))
                }
                ViewerAction(R.drawable.info_24px, "Details") { showInfo = true }
                if (Build.VERSION.SDK_INT >= 30 && current.authority == MediaStore.AUTHORITY) {
                    ViewerAction(R.drawable.delete_24px, "Delete") {
                        val sender = MediaStore.createDeleteRequest(context.contentResolver, listOf(current)).intentSender
                        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                }
            }
        }
    }

    if (showInfo) {
        ExifDialog(
            uri = current,
            onDismiss = { showInfo = false },
            withWriteAccess = ::withWriteAccess,
            onShareClean = {
                val file = PhotoExif.cleanCopy(context, current)
                if (file == null) {
                    Toast.makeText(context, context.getString(R.string.hc_could_not_create_a_copy), Toast.LENGTH_SHORT).show()
                } else {
                    share(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                }
            },
        )
    }
}

@Composable
private fun ViewerAction(icon: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = Color.White)
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ExifDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    withWriteAccess: (() -> Unit) -> Unit,
    onShareClean: () -> Unit,
) {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    val info = remember(uri, reload) { PhotoExif.read(context.contentResolver, uri) }
    val edits = remember(uri, reload) {
        mutableStateMapOf<String, String>().apply { info?.values?.let { putAll(it) } }
    }
    var editing by remember { mutableStateOf(false) }

    fun done(ok: Boolean) {
        Toast.makeText(context, if (ok) "Done" else "This file format does not support metadata changes", Toast.LENGTH_SHORT).show()
        reload++
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hc_details)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (info == null) {
                    Text(stringResource(R.string.hc_no_metadata_available))
                } else {
                    Text("${info.width} × ${info.height}", style = MaterialTheme.typography.bodyMedium)
                    info.latLong?.let {
                        Text(
                            "Location: %.5f, %.5f".format(it[0], it[1]),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    for ((label, tag) in PhotoExif.tags) {
                        if (editing) {
                            OutlinedTextField(
                                value = edits[tag].orEmpty(),
                                onValueChange = { edits[tag] = it },
                                label = { Text(label) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            )
                        } else if (info.values[tag].orEmpty().isNotBlank()) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(info.values[tag].orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    if (!editing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            if (info.hasGps) {
                                AssistChip(onClick = {
                                    withWriteAccess { done(PhotoExif.strip(context.contentResolver, uri, gpsOnly = true)) }
                                }, label = { Text(stringResource(R.string.hc_remove_location)) })
                            }
                            AssistChip(onClick = {
                                withWriteAccess { done(PhotoExif.strip(context.contentResolver, uri, gpsOnly = false)) }
                            }, label = { Text(stringResource(R.string.hc_remove_all)) })
                        }
                        AssistChip(
                            onClick = onShareClean,
                            label = { Text(stringResource(R.string.hc_share_without_metadata)) },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (editing) {
                TextButton(onClick = {
                    withWriteAccess { done(PhotoExif.write(context.contentResolver, uri, edits.toMap())); editing = false }
                }) { Text(stringResource(R.string.hc_save)) }
            } else if (info != null) {
                TextButton(onClick = { editing = true }) { Text(stringResource(R.string.hc_edit)) }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (editing) editing = false else onDismiss() }) {
                Text(if (editing) "Cancel" else "Close")
            }
        },
    )
}
