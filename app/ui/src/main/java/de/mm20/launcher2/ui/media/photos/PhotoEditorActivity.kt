package de.mm20.launcher2.ui.media.photos

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Non destructive photo editor: the result is always saved as a new file. */
class PhotoEditorActivity : BaseActivity() {
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
        val uri = intent.data ?: run { finish(); return }
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        PhotoEditor(uri, onClose = { finish() })
                    }
                }
            }
        }
    }
}

private class Adjust(
    val rotation: Int,
    val flipH: Boolean,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val filter: Int,
    val aspect: Float?,
)

private val filters = listOf("Original", "Mono", "Sepia", "Warm", "Cool", "Invert")
private val aspects = listOf("Free" to null, "1:1" to 1f, "4:3" to 4f / 3f, "3:4" to 3f / 4f, "16:9" to 16f / 9f, "9:16" to 9f / 16f)

private fun matrixFor(a: Adjust): ColorMatrix {
    val m = ColorMatrix()
    val sat = ColorMatrix().apply { setSaturation(a.saturation) }
    m.postConcat(sat)
    val c = a.contrast
    val t = (1f - c) * 127.5f + a.brightness
    m.postConcat(ColorMatrix(floatArrayOf(
        c, 0f, 0f, 0f, t,
        0f, c, 0f, 0f, t,
        0f, 0f, c, 0f, t,
        0f, 0f, 0f, 1f, 0f,
    )))
    when (a.filter) {
        1 -> m.postConcat(ColorMatrix().apply { setSaturation(0f) })
        2 -> m.postConcat(ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f)))
        3 -> m.postConcat(ColorMatrix().apply { setScale(1.1f, 1.0f, 0.88f, 1f) })
        4 -> m.postConcat(ColorMatrix().apply { setScale(0.9f, 1.0f, 1.12f, 1f) })
        5 -> m.postConcat(ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f)))
    }
    return m
}

private fun render(source: Bitmap, a: Adjust): Bitmap {
    val matrix = Matrix().apply {
        postRotate(a.rotation.toFloat())
        if (a.flipH) postScale(-1f, 1f)
    }
    val transformed = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    var x = 0; var y = 0; var w = transformed.width; var h = transformed.height
    a.aspect?.let { ratio ->
        if (w.toFloat() / h > ratio) { w = (h * ratio).toInt(); x = (transformed.width - w) / 2 }
        else { h = (w / ratio).toInt(); y = (transformed.height - h) / 2 }
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(matrixFor(a))
    }
    Canvas(out).drawBitmap(transformed, -x.toFloat(), -y.toFloat(), paint)
    return out
}

private fun decode(context: android.content.Context, uri: Uri, maxSide: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val bmp = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    return PhotoExif.applyOrientation(context, uri, bmp)
}

private fun save(context: android.content.Context, bitmap: Bitmap): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Telos_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Telos")
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return runCatching {
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        uri
    }.getOrElse { resolver.delete(uri, null, null); null }
}

@Composable
private fun PhotoEditor(uri: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) { source = withContext(Dispatchers.IO) { decode(context, uri, 2048) } }

    var rotation by remember { mutableIntStateOf(0) }
    var flipH by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var filter by remember { mutableIntStateOf(0) }
    var aspectIndex by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    fun adjust() = Adjust(rotation, flipH, brightness, contrast, saturation, filter, aspects[aspectIndex].second)
    val bmp = source

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.hc_cancel)) }
            Spacer(Modifier.weight(1f))
            Button(enabled = bmp != null && !saving, onClick = {
                saving = true
                val a = adjust()
                scope.launch {
                    val full = withContext(Dispatchers.IO) { decode(context, uri, 6000) }
                    val saved = withContext(Dispatchers.IO) { full?.let { save(context, render(it, a)) } }
                    saving = false
                    Toast.makeText(context, if (saved != null) "Saved to Pictures/Telos" else "Saving failed", Toast.LENGTH_SHORT).show()
                    if (saved != null) onClose()
                }
            }) { Text(if (saving) "Saving…" else "Save copy") }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            if (bmp != null) {
                val a = adjust()
                val preview = remember(bmp, rotation, flipH, aspectIndex) {
                    render(bmp, Adjust(rotation, flipH, 0f, 1f, 1f, 0, a.aspect))
                }
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(matrixFor(a).array)),
                    modifier = Modifier.fillMaxSize().graphicsLayer(),
                )
            } else CircularProgressIndicator()
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp).heightIn(max = 280.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { rotation = (rotation + 270) % 360 }, label = { Text(stringResource(R.string.hc_rotate_left)) })
                AssistChip(onClick = { rotation = (rotation + 90) % 360 }, label = { Text(stringResource(R.string.hc_rotate_right)) })
                AssistChip(onClick = { flipH = !flipH }, label = { Text(stringResource(R.string.hc_flip)) })
            }
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(aspects.size) { i ->
                    FilterChip(selected = aspectIndex == i, onClick = { aspectIndex = i }, label = { Text(aspects[i].first) })
                }
            }
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters.size) { i ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(filters[i]) })
                }
            }
            Text(stringResource(R.string.hc_brightness), style = MaterialTheme.typography.labelMedium)
            Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -100f..100f)
            Text(stringResource(R.string.hc_contrast), style = MaterialTheme.typography.labelMedium)
            Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..1.8f)
            Text(stringResource(R.string.hc_saturation), style = MaterialTheme.typography.labelMedium)
            Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..2f)
        }
    }
}
