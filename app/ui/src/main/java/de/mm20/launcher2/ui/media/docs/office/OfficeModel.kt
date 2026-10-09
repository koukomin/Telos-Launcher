package de.mm20.launcher2.ui.media.docs.office

import java.io.File
import java.util.zip.ZipFile

enum class OfficeKind { Text, Sheet, Slides }

class OfficeRun(val text: String, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

sealed interface OfficeBlock {
    /** [level]: -1 title, 0 body text, 1..6 heading. [id] is -1 when the paragraph cannot be edited. */
    class Para(
        val id: Int,
        val runs: List<OfficeRun>,
        val level: Int = 0,
        val marker: String? = null,
        val indent: Int = 0,
        val images: List<String> = emptyList(),
        val inTable: Boolean = false,
    ) : OfficeBlock {
        val text: String get() = runs.joinToString("") { it.text }
        val bold: Boolean get() = runs.any { it.text.isNotBlank() } && runs.filter { it.text.isNotBlank() }.all { it.bold }
        val italic: Boolean get() = runs.any { it.text.isNotBlank() } && runs.filter { it.text.isNotBlank() }.all { it.italic }
    }

    class Table(val rows: List<List<List<Para>>>) : OfficeBlock
}

class OfficeSheet(
    val name: String,
    val rows: Int,
    val cols: Int,
    private val values: Map<Long, String>,
    private val formulas: Map<Long, String>,
    /** true when a text that starts with "=" is stored as a formula */
    val formulaEditable: Boolean,
) {
    fun value(row: Int, col: Int): String = values[key(row, col)].orEmpty()
    fun formula(row: Int, col: Int): String? = formulas[key(row, col)]

    companion object {
        fun key(row: Int, col: Int): Long = (row.toLong() shl 20) or col.toLong()
    }
}

class SlideBox(val id: Int, val title: Boolean, val text: String)

class OfficeSlide(val boxes: List<SlideBox>, val images: List<String>)

/** A document that can be shown and (unless [readOnly]) edited. Edits happen on the XML of the package, everything else is copied as it is. */
abstract class OfficeDoc(val file: File) {
    abstract val kind: OfficeKind
    open val readOnly: Boolean get() = false
    var dirty: Boolean = false
        protected set

    protected val zip: ZipFile? by lazy { runCatching { ZipFile(file) }.getOrNull() }

    open fun blocks(): List<OfficeBlock> = emptyList()
    open fun setParagraphText(id: Int, text: String) {}
    open fun toggleBold(id: Int) {}
    open fun toggleItalic(id: Int) {}
    open fun addParagraphAfter(id: Int) {}
    open fun deleteParagraph(id: Int) {}

    open fun sheets(): List<OfficeSheet> = emptyList()
    open fun setCell(sheet: Int, row: Int, col: Int, text: String) {}

    open fun slides(): List<OfficeSlide> = emptyList()
    open val slideAspect: Float get() = 16f / 9f
    open fun setBoxText(slide: Int, box: Int, text: String) {}
    open fun deleteSlide(slide: Int) {}

    /** Writes the complete package with all edits to [dst]. */
    open fun save(dst: File) { throw UnsupportedOperationException() }

    fun imageBytes(entry: String): ByteArray? = runCatching {
        val z = zip ?: return null
        val e = z.getEntry(entry) ?: return null
        if (e.size > 20 * 1024 * 1024) return null
        z.getInputStream(e).use { it.readBytes() }
    }.getOrNull()

    fun close() { runCatching { zip?.close() } }
}
