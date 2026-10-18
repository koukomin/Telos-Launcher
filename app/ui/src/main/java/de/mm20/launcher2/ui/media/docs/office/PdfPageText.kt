package de.mm20.launcher2.ui.media.docs.office

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.StringWriter

/** One glyph on a PDF page, in fractions of the page (origin top-left, like Compose). */
internal data class PdfGlyph(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal data class PdfPageGlyphs(
    val glyphs: List<PdfGlyph>,
)

/** Extracts glyph boxes with PDFBox so a drag can snap to real text. */
internal object PdfPageText {
    fun glyphs(file: File, pageIndex: Int): PdfPageGlyphs = runCatching {
        PDDocument.load(file).use { doc ->
            val page = doc.getPage(pageIndex)
            val box = page.cropBox ?: page.mediaBox
            val w = box.width
            val h = box.height
            if (w <= 0f || h <= 0f) return@use PdfPageGlyphs(emptyList())
            val found = ArrayList<PdfGlyph>(256)
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                    if (textPositions == null) return
                    for (tp in textPositions) {
                        val ch = tp.unicode ?: continue
                        if (ch.isBlank()) continue
                        val x = tp.xDirAdj
                        val width = tp.widthDirAdj
                        val height = tp.heightDir
                        val yTopFromBottom = tp.yDirAdj
                        val top = ((h - yTopFromBottom) / h).coerceIn(0f, 1f)
                        val bottom = ((h - yTopFromBottom + height) / h).coerceIn(0f, 1f)
                        found += PdfGlyph(
                            text = ch,
                            left = (x / w).coerceIn(0f, 1f),
                            top = minOf(top, bottom),
                            right = ((x + width) / w).coerceIn(0f, 1f),
                            bottom = maxOf(top, bottom),
                        )
                    }
                }
            }
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            stripper.sortByPosition = true
            stripper.writeText(doc, StringWriter())
            PdfPageGlyphs(found)
        }
    }.getOrDefault(PdfPageGlyphs(emptyList()))

    fun inRect(glyphs: List<PdfGlyph>, l: Float, t: Float, r: Float, b: Float): List<PdfGlyph> {
        val left = minOf(l, r)
        val right = maxOf(l, r)
        val top = minOf(t, b)
        val bottom = maxOf(t, b)
        return glyphs.filter { g ->
            g.right > left && g.left < right && g.bottom > top && g.top < bottom
        }
    }

    fun union(glyphs: List<PdfGlyph>): FloatArray? {
        if (glyphs.isEmpty()) return null
        return floatArrayOf(
            glyphs.minOf { it.left },
            glyphs.minOf { it.top },
            glyphs.maxOf { it.right },
            glyphs.maxOf { it.bottom },
        )
    }

    fun matches(glyphs: List<PdfGlyph>, query: String): List<List<PdfGlyph>> {
        val q = query.trim()
        if (q.isEmpty() || glyphs.isEmpty()) return emptyList()
        val hay = glyphs.joinToString("") { it.text }
        val ranges = ArrayList<List<PdfGlyph>>()
        var from = 0
        val needle = q.lowercase()
        val lower = hay.lowercase()
        while (from < lower.length) {
            val at = lower.indexOf(needle, from)
            if (at < 0) break
            ranges += glyphs.subList(at, (at + q.length).coerceAtMost(glyphs.size))
            from = at + q.length
        }
        return ranges
    }
}
