package de.mm20.launcher2.ui.media.docs.office

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File

internal const val NS_TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
internal const val NS_TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
internal const val NS_OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
internal const val NS_STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
internal const val NS_FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
internal const val NS_DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
internal const val NS_XLINK = "http://www.w3.org/1999/xlink"
internal const val NS_PRES = "urn:oasis:names:tc:opendocument:xmlns:presentation:1.0"

/** Shared parts of ODT, ODS and ODP: the package is rewritten with a changed content.xml, `mimetype` stays first and uncompressed. */
internal abstract class OdfBase(file: File) : OfficeDoc(file) {
    protected val z = zip!!
    protected val content: Document = OfficeXml.parse(z.bytes("content.xml") ?: error("no content.xml"))
    protected val root: Element get() = content.documentElement
    private val autoStyles: Element? get() = root.child(NS_OFFICE, "automatic-styles")
    private val styleCache = HashMap<String, Triple<Boolean, Boolean, Boolean>>()
    private val created = HashMap<String, String>()
    private var counter = 0
    private val stylesDoc: Document? by lazy { z.bytes("styles.xml")?.let { runCatching { OfficeXml.parse(it) }.getOrNull() } }

    protected fun findStyle(name: String): Element? =
        autoStyles?.children(NS_STYLE, "style")?.firstOrNull { it.attr(NS_STYLE, "name") == name }

    /** bold, italic, underline of an automatic style, following its parent styles. */
    protected fun flags(name: String?, depth: Int = 0): Triple<Boolean, Boolean, Boolean> {
        if (name == null || depth > 6) return Triple(false, false, false)
        styleCache[name]?.let { return it }
        val s = findStyle(name) ?: return Triple(false, false, false).also { styleCache[name] = it }
        val parent = flags(s.attr(NS_STYLE, "parent-style-name"), depth + 1)
        val tp = s.child(NS_STYLE, "text-properties")
        var (b, i, u) = parent
        if (tp != null) {
            tp.attr(NS_FO, "font-weight")?.let { b = it == "bold" || (it.toIntOrNull() ?: 0) >= 600 }
            tp.attr(NS_FO, "font-style")?.let { i = it == "italic" || it == "oblique" }
            tp.attr(NS_STYLE, "text-underline-style")?.let { u = it != "none" }
        }
        return Triple(b, i, u).also { styleCache[name] = it }
    }

    /** Name of an automatic text style like [base] but with the given bold/italic. */
    protected fun deriveStyle(base: String?, bold: Boolean?, italic: Boolean?): String {
        val key = "$base|$bold|$italic"
        created[key]?.let { return it }
        val auto = autoStyles ?: root.insertBefore(content.createElementNS(NS_OFFICE, "office:automatic-styles"), root.firstChild) as Element
        val name = "TelosT${counter++}"
        val src = base?.let { findStyle(it) }
        val style: Element = if (src != null) (src.cloneNode(true) as Element) else content.createElementNS(NS_STYLE, "style:style").also {
            if (base != null) it.setAttributeNS(NS_STYLE, "style:parent-style-name", base)
        }
        style.setAttributeNS(NS_STYLE, "style:name", name)
        style.setAttributeNS(NS_STYLE, "style:family", "text")
        var tp = style.child(NS_STYLE, "text-properties")
        if (tp == null) { tp = content.createElementNS(NS_STYLE, "style:text-properties"); style.appendChild(tp) }
        if (bold != null) {
            val v = if (bold) "bold" else "normal"
            tp.setAttributeNS(NS_FO, "fo:font-weight", v); tp.setAttributeNS(NS_STYLE, "style:font-weight-asian", v); tp.setAttributeNS(NS_STYLE, "style:font-weight-complex", v)
        }
        if (italic != null) {
            val v = if (italic) "italic" else "normal"
            tp.setAttributeNS(NS_FO, "fo:font-style", v); tp.setAttributeNS(NS_STYLE, "style:font-style-asian", v); tp.setAttributeNS(NS_STYLE, "style:font-style-complex", v)
        }
        auto.appendChild(style)
        created[key] = name
        styleCache.remove(name)
        return name
    }

    private val keepers = setOf("bookmark", "bookmark-start", "bookmark-end", "note", "annotation", "annotation-end", "reference-mark", "reference-mark-start", "reference-mark-end")

    /** Replaces the text of a text:p / text:h, keeping pictures, bookmarks and footnotes and the style of the first formatted span. */
    protected fun setOdfText(p: Element, rawText: String) {
        val text = xmlSafe(rawText)
        var spanStyle: String? = null
        for (c in p.elements()) if (c.isEl(NS_TEXT, "span") && c.textContent.isNotEmpty()) { spanStyle = c.attr(NS_TEXT, "style-name"); break }
        var c = p.firstChild
        while (c != null) {
            val next = c.nextSibling
            val keep = c is Element && (c.namespaceURI == NS_DRAW || (c.namespaceURI == NS_TEXT && c.localName in keepers))
            if (!keep) p.removeChild(c)
            c = next
        }
        val target: Element = if (spanStyle != null) content.createElementNS(NS_TEXT, "text:span").also {
            it.setAttributeNS(NS_TEXT, "text:style-name", spanStyle); p.appendChild(it)
        } else p
        val buf = StringBuilder()
        fun flush() { if (buf.isNotEmpty()) { target.appendChild(content.createTextNode(buf.toString())); buf.setLength(0) } }
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when (ch) {
                '\n' -> { flush(); target.appendChild(content.createElementNS(NS_TEXT, "text:line-break")); i++ }
                '\t' -> { flush(); target.appendChild(content.createElementNS(NS_TEXT, "text:tab")); i++ }
                ' ' -> {
                    var n = 0
                    while (i < text.length && text[i] == ' ') { n++; i++ }
                    var rest = n
                    if (buf.isNotEmpty()) { buf.append(' '); rest-- }
                    if (rest > 0) {
                        flush()
                        val s = content.createElementNS(NS_TEXT, "text:s")
                        if (rest > 1) s.setAttributeNS(NS_TEXT, "text:c", rest.toString())
                        target.appendChild(s)
                    }
                }
                '\r' -> i++
                else -> { buf.append(ch); i++ }
            }
        }
        flush()
    }

    protected fun odfText(p: Element): String {
        val sb = StringBuilder()
        fun walk(e: Node) {
            var c = e.firstChild
            while (c != null) {
                when {
                    c.nodeType == Node.TEXT_NODE -> sb.append(c.nodeValue)
                    c is Element && c.namespaceURI == NS_TEXT -> when (c.localName) {
                        "s" -> repeat((c.attr(NS_TEXT, "c")?.toIntOrNull() ?: 1).coerceIn(1, 500)) { sb.append(' ') }
                        "tab" -> sb.append('\t')
                        "line-break" -> sb.append('\n')
                        "note", "annotation", "annotation-end" -> {}
                        else -> walk(c)
                    }
                    c is Element && c.namespaceURI == NS_DRAW -> {}
                    c is Element -> walk(c)
                }
                c = c.nextSibling
            }
        }
        walk(p)
        return sb.toString()
    }

    protected fun odfRuns(p: Element): Pair<List<OfficeRun>, List<String>> {
        val runs = ArrayList<OfficeRun>(); val images = ArrayList<String>()
        fun walk(e: Node, b: Boolean, i: Boolean, u: Boolean) {
            var c = e.firstChild
            while (c != null) {
                when {
                    c.nodeType == Node.TEXT_NODE -> if (!c.nodeValue.isNullOrEmpty()) runs += OfficeRun(c.nodeValue, b, i, u)
                    c is Element && c.namespaceURI == NS_TEXT -> when (c.localName) {
                        "s" -> runs += OfficeRun(" ".repeat((c.attr(NS_TEXT, "c")?.toIntOrNull() ?: 1).coerceIn(1, 500)), b, i, u)
                        "tab" -> runs += OfficeRun("\t", b, i, u)
                        "line-break" -> runs += OfficeRun("\n", b, i, u)
                        "note", "annotation", "annotation-end" -> {}
                        "span" -> { val f = flags(c.attr(NS_TEXT, "style-name")); walk(c, b || f.first, i || f.second, u || f.third) }
                        else -> walk(c, b, i, u)
                    }
                    c is Element && c.namespaceURI == NS_DRAW -> c.descendantsAny("image").forEach { img ->
                        img.attr(NS_XLINK, "href")?.takeIf { !it.contains("://") }?.let { images += it.removePrefix("./") }
                    }
                }
                c = c.nextSibling
            }
        }
        walk(p, false, false, false)
        return runs to images
    }

    private fun Element.descendantsAny(local: String): List<Element> {
        val l = getElementsByTagNameNS(NS_DRAW, local)
        return List(l.length) { l.item(it) as Element }
    }

    protected fun saveContent(dst: File) {
        ZipRewrite.rewrite(file, dst, mapOf("content.xml" to OfficeXml.serialize(content, false)))
    }

    override fun save(dst: File) = saveContent(dst)

    /** Applies bold and/or italic to all text of a paragraph. */
    protected fun applyFormat(p: Element, bold: Boolean?, italic: Boolean?) {
        fun fix(e: Element) {
            var c = e.firstChild
            while (c != null) {
                val next = c.nextSibling
                if (c.nodeType == Node.TEXT_NODE) {
                    if (!c.nodeValue.isNullOrEmpty()) {
                        val span = content.createElementNS(NS_TEXT, "text:span")
                        span.setAttributeNS(NS_TEXT, "text:style-name", deriveStyle(null, bold, italic))
                        e.replaceChild(span, c); span.appendChild(c)
                    }
                } else if (c is Element && c.namespaceURI == NS_TEXT) {
                    when (c.localName) {
                        "span" -> {
                            c.setAttributeNS(NS_TEXT, "text:style-name", deriveStyle(c.attr(NS_TEXT, "style-name"), bold, italic))
                            // nested spans would override it again
                            c.descendantSpans().forEach { s -> s.setAttributeNS(NS_TEXT, "text:style-name", deriveStyle(s.attr(NS_TEXT, "style-name"), bold, italic)) }
                        }
                        "a" -> fix(c)
                    }
                }
                c = next
            }
        }
        fix(p)
        dirty = true
    }

    private fun Element.descendantSpans(): List<Element> {
        val l = getElementsByTagNameNS(NS_TEXT, "span")
        return List(l.length) { l.item(it) as Element }
    }

    protected fun paragraphBoldItalic(p: Element): Pair<Boolean, Boolean> {
        val (runs, _) = odfRuns(p)
        val t = runs.filter { it.text.isNotBlank() }
        return (t.isNotEmpty() && t.all { it.bold }) to (t.isNotEmpty() && t.all { it.italic })
    }

    protected fun listNumbered(styleName: String?, level: Int): Boolean {
        if (styleName == null) return false
        for (d in listOfNotNull(content, stylesDoc)) {
            val l = d.getElementsByTagNameNS(NS_TEXT, "list-style")
            for (k in 0 until l.length) {
                val ls = l.item(k) as Element
                if (ls.attr(NS_STYLE, "name") != styleName) continue
                val lv = ls.elements().firstOrNull { (it.attr(NS_TEXT, "level") ?: "1") == (level + 1).toString() } ?: return false
                return lv.localName == "list-level-style-number"
            }
        }
        return false
    }
}

internal class OdtDoc(file: File) : OdfBase(file) {
    override val kind = OfficeKind.Text
    private var paras = ArrayList<Element>()
    private var cache: List<OfficeBlock>? = null
    private val skipNames = setOf("tracked-changes", "sequence-decls", "variable-decls", "user-field-decls", "dde-connection-decls", "forms")

    override fun blocks(): List<OfficeBlock> {
        cache?.let { return it }
        paras = ArrayList()
        truncated = false
        val out = ArrayList<OfficeBlock>()
        root.child(NS_OFFICE, "body")?.child(NS_OFFICE, "text")?.let { walk(it, out, 0, null, false) }
        cache = out
        return out
    }

    private fun walk(container: Element, out: MutableList<OfficeBlock>, indent: Int, marker: String?, inTable: Boolean) {
        var first = true
        for (c in container.elements()) {
            val m = if (first) marker else null
            when {
                c.isEl(NS_TEXT, "p") || c.isEl(NS_TEXT, "h") -> {
                    if (paras.size < MAX_BLOCKS) out += para(c, indent, m, inTable) else truncated = true
                    first = false
                }
                c.isEl(NS_TEXT, "list") -> {
                    val style = c.attr(NS_TEXT, "style-name")
                    var n = 0
                    for (item in c.children(NS_TEXT, "list-item")) {
                        n++
                        val numbered = listNumbered(style, indent)
                        walk(item, out, indent + 1, if (numbered) "$n." else if (indent == 0) "•" else "–", inTable)
                    }
                    first = false
                }
                c.isEl(NS_TABLE, "table") -> {
                    val rows = ArrayList<List<List<OfficeBlock.Para>>>()
                    fun rowsOf(t: Element) {
                        for (r in t.elements()) {
                            if (r.isEl(NS_TABLE, "table-row")) {
                                rows += r.elements().filter { it.isEl(NS_TABLE, "table-cell") }.map { cell ->
                                    val cb = ArrayList<OfficeBlock>(); walk(cell, cb, 0, null, true)
                                    cb.flatMap { b -> when (b) { is OfficeBlock.Para -> listOf(b); is OfficeBlock.Table -> b.rows.flatten().flatten() } }
                                }
                            } else if (r.namespaceURI == NS_TABLE && r.localName in setOf("table-header-rows", "table-rows", "table-row-group")) rowsOf(r)
                        }
                    }
                    rowsOf(c)
                    out += OfficeBlock.Table(rows)
                    first = false
                }
                c.namespaceURI == NS_TEXT && c.localName in skipNames -> {}
                c.namespaceURI == NS_TEXT || c.namespaceURI == NS_TABLE -> walk(c, out, indent, null, inTable)
            }
        }
    }

    private fun para(p: Element, indent: Int, marker: String?, inTable: Boolean): OfficeBlock.Para {
        val id = paras.size
        paras += p
        val (runs, images) = odfRuns(p)
        val level = if (p.isEl(NS_TEXT, "h")) (p.attr(NS_TEXT, "outline-level")?.toIntOrNull() ?: 1).coerceIn(1, 6) else {
            val sn = p.attr(NS_TEXT, "style-name").orEmpty()
            if (sn.equals("Title", true)) -1 else 0
        }
        return OfficeBlock.Para(id, runs, level, marker, if (marker != null) (indent - 1).coerceAtLeast(0) else 0, images.distinct(), inTable)
    }

    private fun changed() { cache = null; dirty = true }

    override fun setParagraphText(id: Int, text: String) {
        blocks(); val p = paras.getOrNull(id) ?: return
        setOdfText(p, text); changed()
    }

    override fun toggleBold(id: Int) {
        blocks(); val p = paras.getOrNull(id) ?: return
        applyFormat(p, !paragraphBoldItalic(p).first, null); changed()
    }

    override fun toggleItalic(id: Int) {
        blocks(); val p = paras.getOrNull(id) ?: return
        applyFormat(p, null, !paragraphBoldItalic(p).second); changed()
    }

    override fun addParagraphAfter(id: Int) {
        blocks(); val p = paras.getOrNull(id) ?: return
        val np = content.createElementNS(NS_TEXT, "text:p")
        if (!p.isEl(NS_TEXT, "h")) p.attr(NS_TEXT, "style-name")?.let { np.setAttributeNS(NS_TEXT, "text:style-name", it) }
        val parent = p.parentNode as Element
        if (parent.isEl(NS_TEXT, "list-item")) {
            val item = content.createElementNS(NS_TEXT, "text:list-item")
            item.appendChild(np)
            parent.parentNode.insertBefore(item, parent.nextSibling)
        } else parent.insertBefore(np, p.nextSibling)
        changed()
    }

    override fun deleteParagraph(id: Int) {
        blocks(); val p = paras.getOrNull(id) ?: return
        val parent = p.parentNode as Element
        if (parent.isEl(NS_TABLE, "table-cell") && parent.children(NS_TEXT, "p").size + parent.children(NS_TEXT, "h").size <= 1) {
            setOdfText(p, "")
        } else if (parent.isEl(NS_TEXT, "list-item") && parent.elements().size == 1) {
            val list = parent.parentNode
            list.removeChild(parent)
            if (list is Element && list.elements().isEmpty()) list.parentNode.removeChild(list)
        } else parent.removeChild(p)
        changed()
    }
}

internal class OdsDoc(file: File) : OdfBase(file) {
    override val kind = OfficeKind.Sheet
    private var cache: List<OfficeSheet>? = null

    private fun tables(): List<Element> = root.child(NS_OFFICE, "body")?.child(NS_OFFICE, "spreadsheet")?.children(NS_TABLE, "table").orEmpty()

    private fun rowEls(t: Element): List<Element> {
        val out = ArrayList<Element>()
        fun rec(e: Element) {
            for (c in e.elements()) {
                if (c.isEl(NS_TABLE, "table-row")) out += c
                else if (c.namespaceURI == NS_TABLE && c.localName in setOf("table-header-rows", "table-rows", "table-row-group")) rec(c)
            }
        }
        rec(t)
        return out
    }

    private fun cellEls(r: Element) = r.elements().filter { it.isEl(NS_TABLE, "table-cell") || it.isEl(NS_TABLE, "covered-table-cell") }
    private fun rep(e: Element, attr: String) = (e.attr(NS_TABLE, attr)?.toIntOrNull() ?: 1).coerceAtLeast(1)

    private fun cellText(c: Element): String {
        val ps = c.children(NS_TEXT, "p")
        if (ps.isEmpty()) return c.attr(NS_OFFICE, "value").orEmpty()
        return ps.joinToString("\n") { odfText(it) }
    }

    override fun sheets(): List<OfficeSheet> {
        cache?.let { return it }
        val out = tables().map { t ->
            val values = HashMap<Long, String>(); val formulas = HashMap<Long, String>()
            var maxRow = -1; var maxCol = -1
            var r = 0
            var truncated = false
            for (row in rowEls(t)) {
                if (r >= MAX_SHEET_ROWS) { truncated = true; break }
                val rr = rep(row, "number-rows-repeated")
                var col = 0
                var rowHas = false
                val cells = ArrayList<Triple<Int, String, String?>>()
                for (cell in cellEls(row)) {
                    val cr = rep(cell, "number-columns-repeated")
                    val text = cellText(cell)
                    val f = cell.attr(NS_TABLE, "formula")?.removePrefix("of:")
                    if (text.isNotEmpty() || f != null) {
                        for (k in 0 until minOf(cr, 100)) if (col + k < MAX_SHEET_COLS) cells += Triple(col + k, text, f) else truncated = true
                        rowHas = true
                    }
                    col += cr
                }
                if (rowHas) {
                    for (k in 0 until minOf(rr, 200)) for ((cc, text, f) in cells) {
                        val key = OfficeSheet.key(r + k, cc)
                        values[key] = text
                        if (f != null) formulas[key] = f
                        if (cc > maxCol) maxCol = cc
                    }
                    maxRow = r + minOf(rr, 200) - 1
                }
                r += rr
            }
            OfficeSheet(t.attr(NS_TABLE, "name").orEmpty(), maxRow + 1, maxCol + 1, values, formulas, false, truncated)
        }
        cache = out
        return out
    }

    /** Splits a repeated element so that the item at [offset] stands alone, returns that item. */
    private fun isolate(el: Element, attr: String, offset: Int): Element {
        val n = rep(el, attr)
        if (n == 1) return el
        val parent = el.parentNode
        if (offset > 0) {
            val before = el.cloneNode(true) as Element
            setRep(before, attr, offset)
            parent.insertBefore(before, el)
        }
        val after = n - offset - 1
        if (after > 0) {
            val a = el.cloneNode(true) as Element
            setRep(a, attr, after)
            parent.insertBefore(a, el.nextSibling)
        }
        setRep(el, attr, 1)
        return el
    }

    private fun setRep(e: Element, attr: String, n: Int) {
        if (n <= 1) e.removeAttributeNS(NS_TABLE, attr) else e.setAttributeNS(NS_TABLE, "table:$attr", n.toString())
    }

    override fun setCell(sheet: Int, row: Int, col: Int, text: String) {
        val t = tables().getOrNull(sheet) ?: return
        // row
        var pos = 0
        var rowEl: Element? = null
        val rows = rowEls(t)
        for (r in rows) {
            val n = rep(r, "number-rows-repeated")
            if (row < pos + n) { rowEl = isolate(r, "number-rows-repeated", row - pos); break }
            pos += n
        }
        if (rowEl == null) {
            val newRow = content.createElementNS(NS_TABLE, "table:table-row")
            val gap = row - pos
            val last = rows.lastOrNull()
            val parent: Node = last?.parentNode ?: t
            val ref = last?.nextSibling
            if (gap > 0) {
                val g = content.createElementNS(NS_TABLE, "table:table-row")
                setRep(g, "number-rows-repeated", gap)
                g.appendChild(content.createElementNS(NS_TABLE, "table:table-cell"))
                parent.insertBefore(g, ref)
            }
            parent.insertBefore(newRow, ref)
            rowEl = newRow
        }
        // cell
        var cpos = 0
        var cellEl: Element? = null
        val cells = cellEls(rowEl)
        for (c in cells) {
            val n = rep(c, "number-columns-repeated")
            if (col < cpos + n) { cellEl = isolate(c, "number-columns-repeated", col - cpos); break }
            cpos += n
        }
        if (cellEl == null) {
            val gap = col - cpos
            if (gap > 0) {
                val g = content.createElementNS(NS_TABLE, "table:table-cell")
                setRep(g, "number-columns-repeated", gap)
                rowEl.appendChild(g)
            }
            cellEl = content.createElementNS(NS_TABLE, "table:table-cell")
            rowEl.appendChild(cellEl)
        }
        writeCell(cellEl, text)
        cache = null
        dirty = true
    }

    private fun writeCell(cell: Element, text: String) {
        // drop the old value, type and formula (also LibreOffice's calcext copy of the type)
        val attrs = cell.attributes
        val remove = ArrayList<org.w3c.dom.Attr>()
        for (i in 0 until attrs.length) {
            val a = attrs.item(i) as org.w3c.dom.Attr
            val l = a.localName ?: a.name.substringAfter(':')
            if (l in setOf("value-type", "value", "string-value", "date-value", "time-value", "boolean-value", "currency", "formula")) remove += a
        }
        remove.forEach { cell.removeAttributeNode(it) }
        cell.children(NS_TEXT, "p").forEach { cell.removeChild(it) }
        if (text.isEmpty()) return
        if (isPlainNumber(text)) {
            cell.setAttributeNS(NS_OFFICE, "office:value-type", "float")
            cell.setAttributeNS(NS_OFFICE, "office:value", text.trim())
        } else cell.setAttributeNS(NS_OFFICE, "office:value-type", "string")
        val p = content.createElementNS(NS_TEXT, "text:p")
        cell.insertBefore(p, cell.firstChild)
        setOdfText(p, text)
    }
}

internal class OdpDoc(file: File) : OdfBase(file) {
    override val kind = OfficeKind.Slides
    private var aspect = 16f / 9f

    init {
        runCatching {
            val styles = z.bytes("styles.xml")?.let { OfficeXml.parse(it) }
            val l = styles?.getElementsByTagNameNS(NS_STYLE, "page-layout-properties")
            if (l != null && l.length > 0) {
                val e = l.item(0) as Element
                val w = e.attr(NS_FO, "page-width")?.takeWhile { it.isDigit() || it == '.' }?.toFloatOrNull()
                val h = e.attr(NS_FO, "page-height")?.takeWhile { it.isDigit() || it == '.' }?.toFloatOrNull()
                if (w != null && h != null && h > 0) aspect = w / h
            }
        }
    }

    override val slideAspect: Float get() = aspect

    private fun pages(): List<Element> = root.child(NS_OFFICE, "body")?.child(NS_OFFICE, "presentation")?.children(NS_DRAW, "page").orEmpty()

    private fun boxes(page: Element): List<Element> {
        val l = page.getElementsByTagNameNS(NS_DRAW, "text-box")
        return List(l.length) { l.item(it) as Element }
    }

    private fun parasOf(box: Element): List<Element> {
        val out = ArrayList<Element>()
        fun rec(e: Element) { for (c in e.elements()) { if (c.isEl(NS_TEXT, "p") || c.isEl(NS_TEXT, "h")) out += c else if (!c.isEl(NS_DRAW, "text-box")) rec(c) } }
        rec(box)
        return out
    }

    override fun slides(): List<OfficeSlide> = pages().map { page ->
        val bs = boxes(page).mapIndexed { i, b ->
            val frame = b.parentNode as? Element
            SlideBox(i, frame?.attr(NS_PRES, "class") == "title", parasOf(b).joinToString("\n") { odfText(it) })
        }
        val l = page.getElementsByTagNameNS(NS_DRAW, "image")
        val images = List(l.length) { l.item(it) as Element }.mapNotNull { it.attr(NS_XLINK, "href")?.takeIf { h -> !h.contains("://") && h.isNotEmpty() }?.removePrefix("./") }.distinct()
        OfficeSlide(bs, images)
    }

    private fun unit(p: Element, box: Element): Element {
        var u = p
        var n = p.parentNode
        while (n != null && n !== box) { if (n is Element && n.isEl(NS_TEXT, "list-item")) u = n; n = n.parentNode }
        return u
    }

    override fun setBoxText(slide: Int, box: Int, text: String) {
        val page = pages().getOrNull(slide) ?: return
        val b = boxes(page).getOrNull(box) ?: return
        val paras = parasOf(b).toMutableList()
        val lines = text.split("\n")
        if (paras.isEmpty()) {
            val p = content.createElementNS(NS_TEXT, "text:p"); b.appendChild(p); paras += p
        }
        for (i in lines.indices) {
            if (i < paras.size) {
                if (odfText(paras[i]) != lines[i]) setOdfText(paras[i], lines[i])
            } else {
                val lastUnit = unit(paras.last(), b)
                val copy = lastUnit.cloneNode(true) as Element
                lastUnit.parentNode.insertBefore(copy, lastUnit.nextSibling)
                val np = if (copy.isEl(NS_TEXT, "list-item")) parasOf(copy).last() else copy
                setOdfText(np, lines[i])
                paras += np
            }
        }
        for (i in paras.size - 1 downTo maxOf(lines.size, 1)) {
            val u = unit(paras[i], b)
            val parent = u.parentNode
            parent.removeChild(u)
            if (parent is Element && parent.isEl(NS_TEXT, "list") && parent.elements().isEmpty()) parent.parentNode.removeChild(parent)
            paras.removeAt(i)
        }
        dirty = true
    }

    override fun deleteSlide(slide: Int) {
        val page = pages().getOrNull(slide) ?: return
        page.parentNode.removeChild(page)
        dirty = true
    }
}
