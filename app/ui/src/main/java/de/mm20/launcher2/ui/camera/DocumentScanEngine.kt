package de.mm20.launcher2.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.googlecode.leptonica.android.ReadFile
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessPdfRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

internal data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    val file: File,
    val turns: Int = 0,
    val cropX: Float = 0f,
    val cropY: Float = 0f,
    val grayscale: Boolean = true,
)

/** Files, bitmaps, OCR and PDF writing run off Main; only one bounded bitmap is retained per page. */
internal object DocumentScanEngine {
    const val MAX_PAGES = 30
    private const val MAX_INPUT_BYTES = 32 * 1024 * 1024

    suspend fun import(context: Context, uri: Uri, directory: File): ScanPage = withContext(Dispatchers.IO) {
        check(directory.isDirectory || directory.mkdirs())
        val file = File.createTempFile("scan-", ".jpg", directory)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var size = 0
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    size += count; check(size <= MAX_INPUT_BYTES)
                    output.write(buffer, 0, count)
                }
            } } ?: error("Unreadable image")
            // Normalize immediately so page storage cannot accumulate original full-resolution photos.
            val normalized = render(ScanPage(file = file, grayscale = false))
            try { file.outputStream().use { check(normalized.compress(Bitmap.CompressFormat.JPEG, 92, it)) } }
            finally { normalized.recycle() }
            ScanPage(file = file)
        } catch (error: Throwable) { file.delete(); throw error }
    }

    fun render(page: ScanPage): Bitmap {
        require(page.file.length() in 1..MAX_INPUT_BYTES.toLong())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(page.file.path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = CameraImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, 4_000_000)
        }
        val original = BitmapFactory.decodeFile(page.file.path, options) ?: error("Unreadable image")
        try {
            val exif = runCatching { ExifInterface(page.file) }.getOrNull()
            val matrix = Matrix().apply {
                postRotate((exif?.rotationDegrees ?: 0).toFloat())
                if (exif?.isFlipped == true) postScale(-1f, 1f)
                postRotate((page.turns.mod(4) * 90).toFloat())
            }
            val oriented = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            try {
                val left = (oriented.width * page.cropX.coerceIn(0f, .4f)).roundToInt()
                val top = (oriented.height * page.cropY.coerceIn(0f, .4f)).roundToInt()
                val width = (oriented.width - left * 2).coerceAtLeast(1)
                val height = (oriented.height - top * 2).coerceAtLeast(1)
                val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                if (page.grayscale) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                Canvas(result).apply {
                    drawColor(android.graphics.Color.WHITE)
                    drawBitmap(oriented, -left.toFloat(), -top.toFloat(), paint)
                }
                return result
            } finally { if (oriented !== original) oriented.recycle() }
        } finally { original.recycle() }
    }

    suspend fun export(context: Context, pages: List<ScanPage>, languages: Set<String>, directory: File, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        require(pages.size in 1..MAX_PAGES)
        check(directory.isDirectory || directory.mkdirs())
        val output = File(directory, "scan-${UUID.randomUUID()}.pdf")
        try {
            if (languages.isEmpty()) {
                PdfDocument().use { pdf ->
                    for ((index, page) in pages.withIndex()) {
                        coroutineContext.ensureActive()
                        val bitmap = render(page)
                        try {
                            val scale = 595f / bitmap.width
                            val pdfPage = pdf.startPage(PdfDocument.PageInfo.Builder(595, (bitmap.height * scale).roundToInt().coerceAtLeast(1), index + 1).create())
                            pdfPage.canvas.scale(scale, scale)
                            pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                            pdf.finishPage(pdfPage)
                        } finally { bitmap.recycle() }
                        progress(index + 1)
                    }
                    output.outputStream().use(pdf::writeTo)
                }
            } else {
                val data = CameraOcrModels.prepare(context, languages)
                val api = TessBaseAPI()
                try {
                    check(api.init(data.path + "/", languages.sorted().joinToString("+"), TessBaseAPI.OEM_LSTM_ONLY))
                    api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                    val renderer = TessPdfRenderer(api, output.path.removeSuffix(".pdf"))
                    try {
                        check(api.beginDocument(renderer))
                        for ((index, page) in pages.withIndex()) {
                            coroutineContext.ensureActive()
                            val bitmap = render(page)
                            val image = File.createTempFile("ocr-page-", ".jpg", directory)
                            try {
                                image.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)) }
                                val pix = ReadFile.readBitmap(bitmap) ?: error("OCR image conversion failed")
                                try { check(api.addPageToDocument(pix, image.path, renderer)) }
                                finally { pix.recycle() }
                            } finally { bitmap.recycle(); image.delete() }
                            progress(index + 1)
                        }
                        check(api.endDocument(renderer))
                    } finally { renderer.recycle() }
                } finally { api.recycle() }
            }
            check(output.length() > 0)
            output
        } catch (error: Throwable) { output.delete(); throw error }
    }
}
