package de.mm20.launcher2.ui.media.docs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Local, bounded previews. A failed or unsupported decoder leaves the file-type tile visible. */
internal object ViewerThumbnails {
    private const val EDGE = 240
    private val workers = Semaphore(2)
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(context: Context, uri: Uri, name: String, revision: Long): Bitmap? {
        val key = "$uri:$revision"
        synchronized(cache) { cache.get(key) }?.let { return it }
        return workers.withPermit {
            withContext(Dispatchers.IO) {
                synchronized(cache) { cache.get(key) } ?: runCatching { decode(context, uri, name) }
                    .getOrNull()?.also { synchronized(cache) { cache.put(key, it) } }
            }
        }
    }

    private fun decode(context: Context, uri: Uri, name: String): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { resolver.loadThumbnail(uri, Size(EDGE, EDGE), null) }.getOrNull()?.let { return it }
        }
        val mime = resolver.getType(uri).orEmpty()
        val ext = DocumentTypes.ext(name)
        if (mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "avif", "bmp")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, EDGE)
            }
            return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
        if (mime.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "mov", "3gp", "avi", "m4v")) {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, EDGE, EDGE)
                else retriever.getFrameAtTime(-1)?.let { source ->
                    Bitmap.createScaledBitmap(source, EDGE, EDGE, true).also { if (it !== source) source.recycle() }
                }
            } finally { retriever.release() }
        }
        if (ext == "pdf") {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { page ->
                        val height = (EDGE.toFloat() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, EDGE * 2)
                        return Bitmap.createBitmap(EDGE, height, Bitmap.Config.ARGB_8888).apply {
                            eraseColor(Color.WHITE)
                            page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
        if (ext in setOf("docx", "xlsx", "pptx", "odt", "ods", "odp", "epub", "cbz")) {
            // Bound compressed input and selected entry, never expand an archive onto storage.
            val temporary = java.io.File.createTempFile("viewer-thumb-", ".zip", context.cacheDir)
            try {
                resolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 32L * 1024 * 1024) return null
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: return null
                java.util.zip.ZipFile(temporary).use { zip ->
                    val entries = zip.entries()
                    var scanned = 0
                    while (entries.hasMoreElements() && scanned++ < 1000) {
                        val entry = entries.nextElement()
                        val path = entry.name.lowercase()
                        if (path in setOf("docprops/thumbnail.jpeg", "docprops/thumbnail.png", "thumbnails/thumbnail.png") ||
                            (ext in setOf("epub", "cbz") && path.endsWith(".jpg") && (path.contains("cover") || ext == "cbz"))) {
                            val bytes = zip.getInputStream(entry).use { it.readAtMost(2 * 1024 * 1024 + 1) }
                            if (bytes.size > 2 * 1024 * 1024) return null
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, EDGE)
                            })
                        }
                    }
                }
            } finally { temporary.delete() }
        }
        if (DocumentTypes.isText(name)) {
            val text = resolver.openInputStream(uri)?.use { DocumentReaders.decodeText(it.readAtMost(4096)) }.orEmpty()
            if (text.isBlank()) return null
            return Bitmap.createBitmap(EDGE, EDGE, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                canvas.drawColor(Color.rgb(248, 247, 244))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 15f }
                text.lineSequence().flatMap { it.chunked(25).asSequence() }.take(12).forEachIndexed { index, line ->
                    canvas.drawText(line, 12f, 24f + index * 18f, paint)
                }
            }
        }
        return null
    }

    internal fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / sample > target * 2 || height / sample > target * 2) sample *= 2
        return sample
    }
}

@Composable
internal fun ViewerThumbnail(uri: Uri, name: String, revision: Long, modifier: Modifier, fallback: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(null, uri, name, revision) {
        // Lazy rows can be reused for another URI; never retain the previous file's preview.
        value = null
        value = ViewerThumbnails.load(context, uri, name, revision)
    }
    if (bitmap == null) fallback() else Image(bitmap!!.asImageBitmap(), contentDescription = null,
        contentScale = ContentScale.Crop, modifier = modifier)
}

private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (count < 0) break
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}
