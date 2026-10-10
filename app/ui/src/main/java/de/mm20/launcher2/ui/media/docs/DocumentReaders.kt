package de.mm20.launcher2.ui.media.docs

import android.text.Html
import android.util.Xml
import de.mm20.launcher2.ui.media.docs.office.resolvePath
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** What a document is made of, as far as Telos can show it: headings, paragraphs and tables. */
sealed interface DocBlock {
    data class Heading(val text: String, val level: Int) : DocBlock
    data class Paragraph(val text: String) : DocBlock
    data class Table(val rows: List<List<String>>) : DocBlock
    data class Title(val text: String) : DocBlock
}

object DocumentTypes {
    private val pdf = setOf("pdf")
    private val office = setOf("docx", "xlsx", "pptx", "odt", "ods", "odp", "rtf", "epub", "doc", "xls", "ppt")
    private val editableOffice = setOf("docx", "xlsx", "pptx", "odt", "ods", "odp")
    private val legacyOffice = setOf("doc", "xls", "ppt")
    private val text = setOf("txt", "md", "markdown", "csv", "tsv", "log", "json", "xml", "html", "htm", "yml", "yaml", "ini", "conf", "prop", "properties", "kt", "java", "py", "js", "css", "sh", "c", "cpp", "h", "rs", "go", "toml", "sql", "gradle", "srt", "vtt", "ass", "gpx", "kml", "tex", "bat")

    fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
    fun isPdf(name: String) = ext(name) in pdf
    fun isOffice(name: String) = ext(name) in office
    /** formats that have an Office document model (preview and editing), RTF and EPUB are plain views */
    fun isOfficeModel(name: String) = ext(name) in editableOffice || ext(name) in legacyOffice
    fun isLegacyOffice(name: String) = ext(name) in legacyOffice
    fun isText(name: String) = ext(name) in text
    fun isGoogleShortcut(name: String) = GoogleShortcuts.isShortcut(name)
    fun supports(name: String) = isPdf(name) || isOffice(name) || isText(name) || isGoogleShortcut(name)
}

/** Thrown for password protected documents. */
class EncryptedDocumentException : SecurityException("password protected")

/** Headings that Telos adds itself (sheet and slide numbers), supplied by the caller so that they are translated. */
class DocLabels(val sheet: (Int) -> String = { "Sheet $it" }, val slide: (Int) -> String = { "Slide $it" })

/** Reads the text and tables of Office and OpenDocument files, and of EPUB and RTF. The page layout is not reproduced. */
object DocumentReaders {

    /** Decodes a text file: UTF-8 or UTF-16 with byte order mark, UTF-8 without one, otherwise Windows-1253 (Greek) as the common legacy encoding. */
    fun decodeText(bytes: ByteArray): String {
        fun has(vararg b: Int) = bytes.size >= b.size && b.indices.all { (bytes[it].toInt() and 0xFF) == b[it] }
        return when {
            has(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            has(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            has(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> {
                val decoder = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
                    .getOrElse { String(bytes, runCatching { charset("windows-1253") }.getOrDefault(Charsets.ISO_8859_1)) }
            }
        }
    }

    private fun checkEncrypted(file: File, ext: String) {
        if (ext !in setOf("docx", "xlsx", "pptx", "odt", "ods", "odp")) return
        val head = ByteArray(8)
        val n = file.inputStream().use { it.read(head) }
        // password protected OOXML files are OLE2 containers, not ZIP packages
        if (n == 8 && (head[0].toInt() and 0xFF) == 0xD0 && (head[1].toInt() and 0xFF) == 0xCF) throw EncryptedDocumentException()
        if (ext.startsWith("od")) {
            val manifest = runCatching { ZipFile(file).use { z -> z.stream("META-INF/manifest.xml")?.use { it.readBytes().toString(Charsets.UTF_8) } } }.getOrNull()
            if (manifest != null && manifest.contains("encryption-data")) throw EncryptedDocumentException()
        }
    }

    fun read(file: File, name: String, labels: DocLabels = DocLabels()): List<DocBlock> = when (DocumentTypes.ext(name).also { checkEncrypted(file, it) }) {
        "docx" -> docx(file)
        "xlsx" -> xlsx(file, labels)
        "pptx" -> pptx(file, labels)
        "odt", "ods", "odp" -> odf(file, labels)
        "rtf" -> listOf(DocBlock.Paragraph(rtf(file.readText(Charsets.ISO_8859_1))))
        "epub" -> epub(file)
        else -> emptyList()
    }

    private fun parser(input: InputStream): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(input, "UTF-8")
    }

    /** Attribute by local name, whatever prefix the file uses for its namespace. */
    private fun attrLocal(p: XmlPullParser, attr: String): String {
        for (i in 0 until p.attributeCount) if (local(p.getAttributeName(i)) == attr) return p.getAttributeValue(i).orEmpty()
        return ""
    }

    private fun local(name: String?) = name?.substringAfter(':').orEmpty()

    private fun ZipFile.stream(name: String): InputStream? = getEntry(name)?.let { getInputStream(it) }

    // ---- Word ----

    private fun docx(file: File): List<DocBlock> = ZipFile(file).use { zip ->
        val input = zip.stream("word/document.xml") ?: return emptyList()
        val out = mutableListOf<DocBlock>()
        val p = parser(input)
        var text = StringBuilder()
        var style = ""
        var tableDepth = 0
        var rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        var cell = StringBuilder()
        var inText = false
        var inPPr = false
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT && out.size < 20000) {
            when (e) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "tbl" -> { if (tableDepth == 0) rows = mutableListOf(); tableDepth++ }
                    "tr" -> if (tableDepth == 1) row = mutableListOf()
                    "tc" -> if (tableDepth == 1) cell = StringBuilder()
                    "p" -> { text = StringBuilder(); style = "" }
                    "pPr" -> inPPr = true
                    "pStyle" -> style = attrLocal(p, "val")
                    "t" -> inText = true
                    // w:tab inside w:pPr/w:tabs only defines a tab stop
                    "tab" -> if (!inPPr) text.append('\t')
                    "br" -> text.append('\n')
                }
                XmlPullParser.TEXT -> if (inText) text.append(p.text)
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "t" -> inText = false
                    "pPr" -> inPPr = false
                    "p" -> {
                        val t = text.toString()
                        if (tableDepth > 0) { if (tableDepth == 1) { if (cell.isNotEmpty()) cell.append('\n'); cell.append(t) } }
                        else if (t.isNotBlank()) {
                            val level = Regex("""(?i)heading\s*(\d)""").find(style)?.groupValues?.get(1)?.toIntOrNull()
                            out += when {
                                style.equals("Title", true) -> DocBlock.Title(t)
                                level != null -> DocBlock.Heading(t, level)
                                else -> DocBlock.Paragraph(t)
                            }
                        }
                    }
                    "tc" -> if (tableDepth == 1) row.add(cell.toString().trim())
                    "tr" -> if (tableDepth == 1) rows.add(row)
                    "tbl" -> { tableDepth--; if (tableDepth == 0 && rows.isNotEmpty()) out += DocBlock.Table(rows) }
                }
            }
            e = p.next()
        }
        out
    }

    // ---- Excel ----

    private fun xlsx(file: File, labels: DocLabels): List<DocBlock> = ZipFile(file).use { zip ->
        val shared = mutableListOf<String>()
        zip.stream("xl/sharedStrings.xml")?.use { s ->
            val p = parser(s)
            var current = StringBuilder()
            var inT = false
            var inPhonetic = false
            var e = p.eventType
            while (e != XmlPullParser.END_DOCUMENT) {
                when (e) {
                    XmlPullParser.START_TAG -> when (local(p.name)) { "si" -> current = StringBuilder(); "t" -> inT = true; "rPh" -> inPhonetic = true }
                    XmlPullParser.TEXT -> if (inT && !inPhonetic) current.append(p.text)
                    XmlPullParser.END_TAG -> when (local(p.name)) { "t" -> inT = false; "rPh" -> inPhonetic = false; "si" -> shared += current.toString() }
                }
                e = p.next()
            }
        }
        val names = mutableListOf<String>()
        zip.stream("xl/workbook.xml")?.use { s ->
            val p = parser(s)
            var e = p.eventType
            while (e != XmlPullParser.END_DOCUMENT) {
                if (e == XmlPullParser.START_TAG && local(p.name) == "sheet") names += p.getAttributeValue(null, "name").orEmpty()
                e = p.next()
            }
        }
        val sheets = zip.entries().asSequence().map { it.name }.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sortedBy { it.removePrefix("xl/worksheets/sheet").removeSuffix(".xml").toIntOrNull() ?: 0 }.toList()
        val out = mutableListOf<DocBlock>()
        sheets.forEachIndexed { index, entry ->
            out += DocBlock.Heading(names.getOrNull(index)?.ifEmpty { null } ?: labels.sheet(index + 1), 2)
            val rows = mutableListOf<List<String>>()
            zip.stream(entry)!!.use { s ->
                val p = parser(s)
                var cells = HashMap<Int, String>()
                var type = ""
                var column = 0
                var value = StringBuilder()
                var inValue = false
                var e = p.eventType
                while (e != XmlPullParser.END_DOCUMENT && rows.size < 3000) {
                    when (e) {
                        XmlPullParser.START_TAG -> when (local(p.name)) {
                            "row" -> cells = HashMap()
                            "c" -> {
                                type = p.getAttributeValue(null, "t").orEmpty()
                                column = columnIndex(p.getAttributeValue(null, "r").orEmpty())
                                value = StringBuilder()
                            }
                            "v", "t" -> inValue = true
                        }
                        XmlPullParser.TEXT -> if (inValue) value.append(p.text)
                        XmlPullParser.END_TAG -> when (local(p.name)) {
                            "v", "t" -> inValue = false
                            "c" -> cells[column] = when (type) {
                                "s" -> shared.getOrNull(value.toString().trim().toIntOrNull() ?: -1).orEmpty()
                                "b" -> if (value.toString().trim() == "1") "TRUE" else "FALSE"
                                else -> value.toString()
                            }
                            "row" -> if (rows.size < 3000) {
                                val width = (cells.keys.maxOrNull() ?: -1) + 1
                                rows += List(minOf(width, 60)) { cells[it].orEmpty() }
                            }
                        }
                    }
                    e = p.next()
                }
            }
            if (rows.isNotEmpty()) out += DocBlock.Table(rows)
        }
        out
    }

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (c in ref) { if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1) else break }
        return n - 1
    }

    // ---- PowerPoint ----

    private fun pptx(file: File, labels: DocLabels): List<DocBlock> = ZipFile(file).use { zip ->
        val slides = zip.entries().asSequence().map { it.name }.filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            .sortedBy { it.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0 }.toList()
        val out = mutableListOf<DocBlock>()
        slides.forEachIndexed { i, entry ->
            out += DocBlock.Heading(labels.slide(i + 1), 2)
            val p = parser(zip.stream(entry)!!)
            var text = StringBuilder()
            var inT = false
            var e = p.eventType
            while (e != XmlPullParser.END_DOCUMENT) {
                when (e) {
                    XmlPullParser.START_TAG -> when (local(p.name)) { "p" -> text = StringBuilder(); "t" -> inT = true }
                    XmlPullParser.TEXT -> if (inT) text.append(p.text)
                    XmlPullParser.END_TAG -> when (local(p.name)) {
                        "t" -> inT = false
                        "p" -> if (text.isNotBlank()) out += DocBlock.Paragraph(text.toString())
                    }
                }
                e = p.next()
            }
        }
        out
    }

    // ---- OpenDocument (text, spreadsheet, presentation) ----

    private fun odf(file: File, labels: DocLabels): List<DocBlock> = ZipFile(file).use { zip ->
        val input = zip.stream("content.xml") ?: return emptyList()
        val p = parser(input)
        val out = mutableListOf<DocBlock>()
        var text = StringBuilder()
        var headingLevel = 0
        var tableDepth = 0
        var rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        var cell = StringBuilder()
        var slide = 0
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT && out.size < 20000) {
            when (e) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "table:table" -> { if (tableDepth == 0) { rows = mutableListOf(); p.getAttributeValue(null, "table:name")?.let { out += DocBlock.Heading(it, 2) } }; tableDepth++ }
                    "table:table-row" -> if (tableDepth == 1) row = mutableListOf()
                    "table:table-cell" -> if (tableDepth == 1) cell = StringBuilder()
                    "draw:page" -> { slide++; out += DocBlock.Heading(labels.slide(slide), 2) }
                    "text:h" -> { text = StringBuilder(); headingLevel = p.getAttributeValue(null, "text:outline-level")?.toIntOrNull() ?: 1 }
                    "text:p" -> { text = StringBuilder(); headingLevel = 0 }
                    "text:s" -> text.append(' ')
                    "text:tab" -> text.append('\t')
                    "text:line-break" -> text.append('\n')
                }
                XmlPullParser.TEXT -> text.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "text:h", "text:p" -> {
                        val t = text.toString()
                        if (tableDepth > 0) { if (tableDepth == 1) { if (cell.isNotEmpty()) cell.append('\n'); cell.append(t) } }
                        else if (t.isNotBlank()) out += if (p.name == "text:h") DocBlock.Heading(t, headingLevel.coerceIn(1, 6)) else DocBlock.Paragraph(t)
                        text = StringBuilder()
                    }
                    "table:table-cell" -> if (tableDepth == 1) row.add(cell.toString().trim())
                    "table:table-row" -> if (tableDepth == 1 && rows.size < 3000 && row.any { it.isNotEmpty() }) rows.add(row)
                    "table:table" -> { tableDepth--; if (tableDepth == 0 && rows.isNotEmpty()) out += DocBlock.Table(rows) }
                }
            }
            e = p.next()
        }
        out
    }

    // ---- RTF ----

    fun rtf(raw: String): String {
        val out = StringBuilder()
        // \'xx escapes are bytes of the document code page (\ansicpgN, for example 1253 for Greek)
        val codepage = Regex("""\\ansicpg(\d{3,5})""").find(raw.take(4000))?.groupValues?.get(1)
        val charset = runCatching { charset(if (codepage == "65001") "UTF-8" else "windows-$codepage") }.getOrDefault(Charsets.ISO_8859_1)
        val pending = java.io.ByteArrayOutputStream()
        fun flushBytes() { if (pending.size() > 0) { out.append(String(pending.toByteArray(), charset)); pending.reset() } }
        var depth = 0
        var skipDepth = -1
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (!(c == '\\' && raw.getOrNull(i + 1) == '\'')) flushBytes()
            when {
                c == '{' -> {
                    depth++
                    // groups such as the font table or pictures are not text
                    if (skipDepth < 0 && (raw.startsWith("{\\*", i) || raw.startsWith("{\\fonttbl", i) || raw.startsWith("{\\colortbl", i) || raw.startsWith("{\\stylesheet", i) || raw.startsWith("{\\pict", i))) {
                        skipDepth = depth
                    }
                    i++
                }
                c == '}' -> { if (depth == skipDepth) skipDepth = -1; depth--; i++ }
                c == '\\' -> {
                    val next = raw.getOrNull(i + 1)
                    when {
                        next == '\'' -> {
                            val hex = raw.substring(i + 2, minOf(i + 4, raw.length))
                            if (skipDepth < 0) hex.toIntOrNull(16)?.let { pending.write(it) }
                            i += 4
                        }
                        next != null && !next.isLetter() -> { if (skipDepth < 0 && (next == '\\' || next == '{' || next == '}')) out.append(next); i += 2 }
                        else -> {
                            var j = i + 1
                            while (j < raw.length && raw[j].isLetter()) j++
                            val word = raw.substring(i + 1, j)
                            var k = j
                            if (k < raw.length && (raw[k] == '-' || raw[k].isDigit())) { k++; while (k < raw.length && raw[k].isDigit()) k++ }
                            val number = raw.substring(j, k).toIntOrNull()
                            if (skipDepth < 0) when (word) {
                                "par", "line" -> out.append('\n')
                                "tab" -> out.append('\t')
                                "u" -> if (number != null) { out.append((if (number < 0) number + 65536 else number).toChar()); if (k < raw.length && raw[k] == '?') k++ }
                            }
                            i = if (k < raw.length && raw[k] == ' ') k + 1 else k
                        }
                    }
                }
                c == '\r' || c == '\n' -> i++
                else -> { if (skipDepth < 0) out.append(c); i++ }
            }
        }
        flushBytes()
        return out.toString().trim()
    }

    // ---- EPUB ----

    private fun epub(file: File): List<DocBlock> = ZipFile(file).use { zip ->
        val container = zip.stream("META-INF/container.xml")?.use { s ->
            val p = parser(s)
            var path: String? = null
            var e = p.eventType
            while (e != XmlPullParser.END_DOCUMENT && path == null) {
                if (e == XmlPullParser.START_TAG && local(p.name) == "rootfile") path = p.getAttributeValue(null, "full-path")
                e = p.next()
            }
            path
        } ?: return emptyList()
        val base = container.substringBeforeLast('/', "")
        val manifest = HashMap<String, String>()
        val spine = mutableListOf<String>()
        zip.stream(container)?.use { s ->
            val p = parser(s)
            var e = p.eventType
            while (e != XmlPullParser.END_DOCUMENT) {
                if (e == XmlPullParser.START_TAG) when (local(p.name)) {
                    "item" -> manifest[p.getAttributeValue(null, "id").orEmpty()] = p.getAttributeValue(null, "href").orEmpty()
                    "itemref" -> spine += p.getAttributeValue(null, "idref").orEmpty()
                }
                e = p.next()
            }
        }
        val out = mutableListOf<DocBlock>()
        for (id in spine) {
            val href = manifest[id] ?: continue
            // hrefs are relative to the package file, may contain ../ and are URL encoded
            val entry = resolvePath(if (base.isEmpty()) "" else "$base/", android.net.Uri.decode(href.substringBefore('#')))
            val ze = zip.getEntry(entry) ?: continue
            if (ze.size > 8L * 1024 * 1024 || out.size >= 20000) continue
            val html = zip.getInputStream(ze).bufferedReader().use { it.readText() }
            val text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().replace("\uFFFC", "").trim()
            text.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { out += DocBlock.Paragraph(it) }
        }
        out
    }
}
