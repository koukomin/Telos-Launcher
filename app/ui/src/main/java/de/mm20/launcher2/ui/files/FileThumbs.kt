package de.mm20.launcher2.ui.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.Color
import androidx.exifinterface.media.ExifInterface
import de.mm20.launcher2.ui.files.remote.RemotePath
import de.mm20.launcher2.ui.files.vault.VaultPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Collections
import java.util.zip.ZipFile

/**
 * Previews for the picture view of Telos Files: pictures, video frames, the first page of a PDF, the thumbnail stored
 * in OpenDocument and Office files, EPUB covers, app icons and album art. Every decode is guarded: a file that cannot
 * be previewed simply gets the coloured type tile.
 */
internal object FileThumbs {
    /** Largest edge of a preview in pixels */
    const val MAX_PX = 512
    const val MEDIUM_PX = 256
    const val LARGE_PX = 384

    private const val MAX_WHOLE_FILE = 200L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 4 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16).toInt().coerceIn(8 * 1024 * 1024, 48 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val failed: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val gate = Semaphore(3)
    /** PdfRenderer is not thread safe: only one PDF is rendered at a time */
    private val pdfLock = Any()

    private val photoExts = setOf("jpg", "jpeg", "png", "webp")
    private val odfExts = setOf("odt", "ods", "odp")
    private val ooxmlExts = setOf("docx", "xlsx", "pptx")

    fun keyOf(e: FsEntry, req: Int) = "${e.path}|${e.modified}|${e.size}|$req"

    fun peek(key: String): Bitmap? = cache.get(key)

    /** Whether a preview can be tried at all: only plain files and folders on this phone, never vaults, clouds or archives */
    fun eligible(context: Context, e: FsEntry): Boolean {
        if (e.path.isEmpty() || RemotePath.isRemote(e.path) || ArchivePath.isArchive(e.path) || VaultPath.isVault(e.path)) return false
        if (e.isLink && e.isDir) return false
        // the private storage of this app (and of other apps' data folders) is not previewed
        val dataDir = context.applicationInfo.dataDir
        if (dataDir != null && e.path.startsWith(dataDir)) return false
        return true
    }

    suspend fun load(context: Context, e: FsEntry, req: Int): Bitmap? {
        val key = keyOf(e, req)
        cache.get(key)?.let { return it }
        if (key in failed) return null
        val app = context.applicationContext
        val bitmap = gate.withPermit {
            withContext(Dispatchers.IO) { runCatching { decode(app, e, req.coerceIn(32, MAX_PX)) }.getOrNull() }
        }
        if (bitmap != null) {
            cache.put(key, bitmap)
        } else {
            if (failed.size > 4000) failed.clear()
            failed.add(key)
        }
        return bitmap
    }

    private fun decode(context: Context, e: FsEntry, req: Int): Bitmap? {
        val file = File(e.path)
        if (e.isDir) return folder(file, req)
        if (!file.isFile || !file.canRead()) return null
        val ext = e.extension
        return when {
            e.kind == FileKind.Image && ext != "svg" -> if (file.length() > MAX_WHOLE_FILE) null else image(file, req)
            e.kind == FileKind.Video -> video(file, req)
            e.kind == FileKind.Audio -> audio(file, req)
            ext == "pdf" -> if (file.length() > MAX_WHOLE_FILE) null else pdf(file, req)
            ext in odfExts -> zipThumb(file, req, listOf("Thumbnails/thumbnail.png"))
            ext in ooxmlExts -> zipThumb(file, req, listOf("docProps/thumbnail.jpeg", "docProps/thumbnail.png", "docProps/thumbnail.jpg"))
            ext == "epub" -> epub(file, req)
            ext == "apk" -> if (file.length() > MAX_WHOLE_FILE) null else apk(context, file, req)
            else -> null
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The centre (or the top) square of [src], at most [req] pixels wide */
    private fun squareFit(src: Bitmap, req: Int, top: Boolean = false): Bitmap {
        val m = minOf(src.width, src.height)
        if (m <= 0) return src
        val sq = if (src.width == m && src.height == m) src
        else Bitmap.createBitmap(src, (src.width - m) / 2, if (top) 0 else (src.height - m) / 2, m, m)
        val target = minOf(req, m)
        return if (sq.width == target) sq else Bitmap.createScaledBitmap(sq, target, target, true)
    }

    private fun sampleFor(width: Int, height: Int, req: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= req && height / (sample * 2) >= req) sample *= 2
        return sample
    }

    private fun decodeBytes(bytes: ByteArray, req: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, req) }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return squareFit(bmp, req)
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > max) return null
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- decoders

    private fun image(file: File, req: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, req) }
        var bmp = BitmapFactory.decodeFile(file.path, opts) ?: return null
        if (file.extension.lowercase() in setOf("jpg", "jpeg")) {
            val orientation = runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> {}
            }
            if (!matrix.isIdentity) bmp = runCatching { Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true) }.getOrDefault(bmp)
        }
        return squareFit(bmp, req)
    }

    private fun video(file: File, req: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            val thumb = runCatching { ThumbnailUtils.createVideoThumbnail(file, Size(req, req), null) }.getOrNull()
            if (thumb != null) return squareFit(thumb, req)
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            val frame = if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, req * 2, req * 2)
            } else {
                retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: retriever.getFrameAtTime()
            frame?.let { squareFit(it, req) }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun audio(file: File, req: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.embeddedPicture?.let { decodeBytes(it, req) }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun pdf(file: File, req: Int): Bitmap? = synchronized(pdfLock) {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(fd)
            try {
                if (renderer.pageCount <= 0) return null
                val page = renderer.openPage(0)
                try {
                    if (page.width <= 0 || page.height <= 0) return null
                    val w = req
                    val h = (req.toLong() * page.height / page.width).toInt().coerceIn(1, req * 3)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    squareFit(bmp, req, top = true)
                } finally {
                    runCatching { page.close() }
                }
            } finally {
                runCatching { renderer.close() }
            }
        } finally {
            runCatching { fd.close() }
        }
    }

    private fun zipThumb(file: File, req: Int, names: List<String>): Bitmap? {
        ZipFile(file).use { zip ->
            for (name in names) {
                val entry = zip.getEntry(name) ?: continue
                if (entry.size > MAX_ENTRY_BYTES) continue
                val bytes = zip.getInputStream(entry).use { readLimited(it, MAX_ENTRY_BYTES) } ?: continue
                decodeBytes(bytes, req)?.let { return it }
            }
        }
        return null
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""(?:^|\s)${Regex.escape(name)}\s*=\s*["']([^"']*)["']""").find(tag)?.groupValues?.get(1)

    private fun epub(file: File, req: Int): Bitmap? {
        ZipFile(file).use { zip ->
            fun text(name: String, max: Int): String? {
                val entry = zip.getEntry(name) ?: return null
                if (entry.size > max) return null
                return zip.getInputStream(entry).use { readLimited(it, max) }?.toString(Charsets.UTF_8)
            }
            val container = text("META-INF/container.xml", 64 * 1024) ?: return null
            val opfPath = Regex("""full-path\s*=\s*["']([^"']+)["']""").find(container)?.groupValues?.get(1) ?: return null
            val opf = text(opfPath, 1024 * 1024) ?: return null
            val items = Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(opf).map { it.value }.toList()
            val metaCover = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(opf).map { it.value }
                .firstOrNull { attr(it, "name") == "cover" }?.let { attr(it, "content") }
            fun isImage(tag: String) = attr(tag, "media-type")?.startsWith("image/") == true
            val href = items.firstOrNull { attr(it, "properties")?.split(' ')?.contains("cover-image") == true }?.let { attr(it, "href") }
                ?: items.firstOrNull { metaCover != null && attr(it, "id") == metaCover }?.let { attr(it, "href") }
                ?: items.firstOrNull { isImage(it) && ((attr(it, "id") ?: "").contains("cover", true) || (attr(it, "href") ?: "").contains("cover", true)) }?.let { attr(it, "href") }
                ?: return null
            val dir = opfPath.substringBeforeLast('/', "")
            val decoded = runCatching { java.net.URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
            val full = File("/" + (if (dir.isEmpty()) "" else "$dir/") + decoded).normalize().path.removePrefix("/")
            val entry = zip.getEntry(full) ?: zip.getEntry(decoded) ?: return null
            if (entry.size > MAX_ENTRY_BYTES) return null
            val bytes = zip.getInputStream(entry).use { readLimited(it, MAX_ENTRY_BYTES) } ?: return null
            return decodeBytes(bytes, req)
        }
    }

    private fun apk(context: Context, file: File, req: Int): Bitmap? {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(file.path, 0) ?: return null
        val ai = info.applicationInfo ?: return null
        ai.sourceDir = file.path
        ai.publicSourceDir = file.path
        val icon = ai.loadIcon(pm) ?: return null
        val bmp = Bitmap.createBitmap(req, req, Bitmap.Config.ARGB_8888)
        val inset = (req * 0.16f).toInt()
        icon.setBounds(inset, inset, req - inset, req - inset)
        icon.draw(Canvas(bmp))
        return bmp
    }

    /** Up to four of the first pictures in a folder, as one tile */
    private fun folder(dir: File, req: Int): Bitmap? {
        if (VaultPath.isVaultFolder(dir)) return null
        val children = dir.listFiles() ?: return null
        val pics = children.asSequence().take(400)
            .filter { it.isFile && it.extension.lowercase() in photoExts && it.length() <= 30L * 1024 * 1024 }
            .sortedBy { it.name.lowercase() }.take(4).toList()
        if (pics.isEmpty()) return null
        if (pics.size == 1) return image(pics[0], req)
        val gap = 3
        val cell = (req - gap) / 2
        val out = Bitmap.createBitmap(req, req, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        var drawn = 0
        pics.forEachIndexed { i, f ->
            val b = runCatching { image(f, cell) }.getOrNull() ?: return@forEachIndexed
            canvas.drawBitmap(b, ((i % 2) * (cell + gap)).toFloat(), ((i / 2) * (cell + gap)).toFloat(), null)
            drawn++
        }
        return if (drawn == 0) null else out
    }
}

/** The colour of the tile for [e] when it has no preview (same family as in Telos Viewer) */
internal fun thumbColor(e: FsEntry): Color {
    if (e.isDir) return FileKind.Folder.color
    return when (e.extension) {
        "pdf" -> Color(0xFFD32F2F)
        "doc", "docx", "odt", "rtf", "gdoc" -> Color(0xFF1565C0)
        "xls", "xlsx", "ods", "csv", "tsv", "gsheet" -> Color(0xFF2E7D32)
        "ppt", "pptx", "odp", "gslides" -> Color(0xFFC2410C)
        "epub" -> Color(0xFF6A1B9A)
        else -> when (e.kind) {
            FileKind.Archive -> Color(0xFF6D4C41)
            FileKind.Audio -> Color(0xFFD81B60)
            FileKind.Video -> Color(0xFF00897B)
            FileKind.Image -> Color(0xFFAB47BC)
            FileKind.Apk -> Color(0xFF43A047)
            else -> Color(0xFF546E7A)
        }
    }
}
