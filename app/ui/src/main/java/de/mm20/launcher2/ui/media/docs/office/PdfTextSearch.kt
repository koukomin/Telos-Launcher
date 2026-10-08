package de.mm20.launcher2.ui.media.docs.office

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

class PdfHit(val page: Int, val snippet: String)

/** Text search in a PDF with PDFBox. The text of a page is extracted when it is first needed and kept. */
class PdfTextSearch(context: Context, private val file: File) {
    private val appContext = context.applicationContext
    private var doc: PDDocument? = null
    private val pages = HashMap<Int, String>()
    var pageCount = 0
        private set
    var hasText = true
        private set

    @Synchronized
    private fun open(): PDDocument {
        doc?.let { return it }
        PDFBoxResourceLoader.init(appContext)
        return PDDocument.load(file).also { doc = it; pageCount = it.numberOfPages }
    }

    @Synchronized
    private fun pageText(i: Int): String = pages.getOrPut(i) {
        val d = open()
        PDFTextStripper().apply { startPage = i + 1; endPage = i + 1 }.getText(d).orEmpty()
    }

    /** Runs on a background thread. [onProgress] gets the number of pages searched. */
    fun search(query: String, onProgress: (Int) -> Unit = {}): List<PdfHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val n = open().numberOfPages
        val out = ArrayList<PdfHit>()
        var any = false
        for (i in 0 until n) {
            val t = pageText(i)
            if (t.isNotBlank()) any = true
            var from = 0
            while (out.size < 300) {
                val at = t.indexOf(q, from, ignoreCase = true)
                if (at < 0) break
                val s = (at - 40).coerceAtLeast(0); val e = (at + q.length + 60).coerceAtMost(t.length)
                out += PdfHit(i, t.substring(s, e).replace(Regex("\\s+"), " ").trim())
                from = at + q.length
            }
            if (i % 5 == 0) onProgress(i + 1)
            if (out.size >= 300) break
        }
        hasText = any || out.isNotEmpty()
        return out
    }

    @Synchronized
    fun close() { runCatching { doc?.close() }; doc = null }
}
