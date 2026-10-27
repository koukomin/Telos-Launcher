package de.mm20.launcher2.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class DocumentScanEngineTest {
    @Test fun importCropRotateAndExportMultiplePages() = runBlocking {
        withContext(Dispatchers.IO) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = File(context.cacheDir, "scanner-test-${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val input = File(directory, "original.png")
                val image = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
                image.eraseColor(Color.RED)
                try { input.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { image.recycle() }
                val imported = DocumentScanEngine.import(context, Uri.fromFile(input), directory)
                val edited = imported.copy(turns = 1, cropX = .1f, cropY = .2f)
                val rendered = DocumentScanEngine.render(edited)
                try { assertEquals(320, rendered.width); assertEquals(120, rendered.height) } finally { rendered.recycle() }
                val progress = ArrayList<Int>()
                val output = DocumentScanEngine.export(context, listOf(imported, edited), emptySet(), directory) { progress += it }
                ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    val renderer = PdfRenderer(descriptor)
                    try { assertEquals(2, renderer.pageCount) } finally { renderer.close() }
                }
                assertEquals(listOf(1,2), progress)
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun greekAndEnglishProduceSearchablePdfText() = runBlocking {
        withContext(Dispatchers.IO) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = File(context.cacheDir, "scanner-ocr-test-${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val image = Bitmap.createBitmap(1000, 1200, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(image).apply { drawColor(Color.WHITE) }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL) }
                for (i in 0..5) canvas.drawText(if(i % 2 == 0) "Ελληνική γλώσσα και κείμενο" else "English searchable document", 60f, 140f + i * 130f, paint)
                val file = File(directory, "languages.jpg")
                try { file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 95, it) } } finally { image.recycle() }
                val output = DocumentScanEngine.export(context, listOf(ScanPage(file = file)), setOf("ell","eng"), directory) {}
                PDFBoxResourceLoader.init(context)
                PDDocument.load(output).use { pdf ->
                    assertEquals(1, pdf.numberOfPages)
                    val text = PDFTextStripper().getText(pdf)
                    assertTrue(text.contains("English", ignoreCase = true))
                    assertTrue(text.any { it in '\u0370'..'\u03ff' })
                }
            } finally { directory.deleteRecursively() }
        }
    }
}
