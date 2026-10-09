package de.mm20.launcher2.ui.media.docs.office

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

/** Word document: paragraph text and bold/italic are edited in word/document.xml, everything else in the package stays as it is. */
internal class DocxDoc(file: File) : OfficeDoc(file) {
    override val kind = OfficeKind.Text

    private val z = zip!!
    private val docXml: Document = OfficeXml.parse(z.bytes("word/document.xml") ?: error("no document.xml"))
    private val rels = readRels(z, "word/_rels/document.xml.rels", "word/")
    private val styleNames = HashMap<String, String>()
    private val numFmt = HashMap<Pair<String, Int>, String>() // (numId, ilvl) to numFmt
    private var paras = ArrayList<Element>()
    private var cache: List<OfficeBlock>? = null

    init {
        z.bytes("word/styles.xml")?.let { b ->
            runCatching {
                OfficeXml.parse(b).documentElement.children(NS_W, "style").forEach { s ->
                    val id = s.attr(NS_W, "styleId") ?: return@forEach
                    styleNames[id] = s.child(NS_W, "name")?.attr(NS_W, "val") ?: id
                }
            }
        }
        z.bytes("word/numbering.xml")?.let { b ->
            runCatching {
                val root = OfficeXml.parse(b).documentElement
                val abstract = HashMap<String, Map<Int, String>>()
                for (a in root.children(NS_W, "abstractNum")) {
                    val lv = HashMap<Int, String>()
                    for (l in a.children(NS_W, "lvl")) lv[(l.attr(NS_W, "ilvl") ?: "0").toIntOrNull() ?: 0] = l.child(NS_W, "numFmt")?.attr(NS_W, "val") ?: "decimal"
                    abstract[a.attr(NS_W, "abstractNumId") ?: ""] = lv
                }
                for (n in root.children(NS_W, "num")) {
                    val id = n.attr(NS_W, "numId") ?: continue
                    val abs = abstract[n.child(NS_W, "abstractNumId")?.attr(NS_W, "val") ?: ""] ?: continue
                    abs.forEach { (lvl, fmt) -> numFmt[id to lvl] = fmt }
                }
            }
        }
    }

    override fun blocks(): List<OfficeBlock> {
        cache?.let { return it }
        val list = ArrayList<Element>()
        paras = list
        truncated = false
        val out = ArrayList<OfficeBlock>()
        val counters = HashMap<Pair<String, Int>, Int>()
        val body = docXml.documentElement.child(NS_W, "body")
        if (body != null) walk(body, out, counters, false)
        cache = out
        return out
    }

    private fun walk(container: Element, out: MutableList<OfficeBlock>, counters: HashMap<Pair<String, Int>, Int>, inTable: Boolean) {
        for (c in container.elements()) {
            if (c.namespaceURI != NS_W) continue
            when (c.localName) {
                "p" -> if (paras.size < MAX_BLOCKS) out += para(c, counters, inTable) else truncated = true
                "tbl" -> if (paras.size >= MAX_BLOCKS) truncated = true else {
                    val rows = c.children(NS_W, "tr").map { tr ->
                        tr.children(NS_W, "tc").map { tc ->
                            val cell = ArrayList<OfficeBlock>()
                            walk(tc, cell, counters, true)
                            flatten(cell)
                        }
                    }
                    out += OfficeBlock.Table(rows)
                }
                "sdt" -> c.child(NS_W, "sdtContent")?.let { walk(it, out, counters, inTable) }
                "customXml", "ins" -> walk(c, out, counters, inTable)
            }
        }
    }

    private fun flatten(blocks: List<OfficeBlock>): List<OfficeBlock.Para> = blocks.flatMap {
        when (it) { is OfficeBlock.Para -> listOf(it); is OfficeBlock.Table -> it.rows.flatten().flatten() }
    }

    private fun para(p: Element, counters: HashMap<Pair<String, Int>, Int>, inTable: Boolean): OfficeBlock.Para {
        val id = paras.size
        paras.add(p)
        val pPr = p.child(NS_W, "pPr")
        val styleId = pPr?.child(NS_W, "pStyle")?.attr(NS_W, "val").orEmpty()
        val styleName = styleNames[styleId] ?: styleId
        var level = 0
        if (styleName.equals("Title", true) || styleId.equals("Title", true)) level = -1
        else {
            val m = Regex("""(?i)(?:heading|überschrift|titre)\s*(\d)""").find(styleName) ?: Regex("""(?i)heading\s*(\d)""").find(styleId)
            level = m?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 6)
                ?: pPr?.child(NS_W, "outlineLvl")?.attr(NS_W, "val")?.toIntOrNull()?.let { (it + 1).coerceIn(1, 6) } ?: 0
        }
        var marker: String? = null
        var indent = 0
        val numPr = pPr?.child(NS_W, "numPr")
        val numId = numPr?.child(NS_W, "numId")?.attr(NS_W, "val")
        if (numId != null && numId != "0") {
            val ilvl = numPr!!.child(NS_W, "ilvl")?.attr(NS_W, "val")?.toIntOrNull() ?: 0
            indent = ilvl
            val fmt = numFmt[numId to ilvl] ?: "bullet"
            if (fmt == "bullet" || fmt == "none") marker = if (ilvl == 0) "•" else "–"
            else {
                val n = (counters[numId to ilvl] ?: 0) + 1
                counters[numId to ilvl] = n
                counters.keys.filter { it.first == numId && it.second > ilvl }.forEach { counters.remove(it) }
                marker = when (fmt) {
                    "lowerLetter" -> letters(n).lowercase() + "."
                    "upperLetter" -> letters(n) + "."
                    "lowerRoman" -> roman(n).lowercase() + "."
                    "upperRoman" -> roman(n) + "."
                    else -> "$n."
                }
            }
        }
        val runs = ArrayList<OfficeRun>()
        val images = ArrayList<String>()
        collect(p, runs, images)
        return OfficeBlock.Para(id, runs, level, marker, indent, images.distinct(), inTable)
    }

    private fun collect(parent: Element, runs: MutableList<OfficeRun>, images: MutableList<String>) {
        for (c in parent.elements()) {
            if (c.namespaceURI != NS_W) continue
            when (c.localName) {
                "r" -> run(c, runs, images)
                "hyperlink", "ins", "smartTag", "fldSimple", "customXml", "sdt", "sdtContent" -> collect(c, runs, images)
            }
        }
    }

    private fun run(r: Element, runs: MutableList<OfficeRun>, images: MutableList<String>) {
        val rPr = r.child(NS_W, "rPr")
        val bold = OoxmlText.isOn(rPr?.child(NS_W, "b"))
        val italic = OoxmlText.isOn(rPr?.child(NS_W, "i"))
        val u = rPr?.child(NS_W, "u")?.attr(NS_W, "val")
        val sb = StringBuilder()
        for (c in r.elements()) {
            if (c.namespaceURI != NS_W) continue
            when (c.localName) {
                "t" -> sb.append(c.textContent)
                "tab" -> sb.append('\t')
                "br", "cr" -> sb.append('\n')
                "noBreakHyphen" -> sb.append('-')
            }
        }
        for (b in r.descendants(NS_A, "blip")) b.attr(NS_REL, "embed")?.let { rels[it] }?.let { images += it }
        for (v in r.descendants(NS_VML, "imagedata")) v.attr(NS_REL, "id")?.let { rels[it] }?.let { images += it }
        if (sb.isNotEmpty()) runs += OfficeRun(sb.toString(), bold, italic, u != null && u != "none")
    }

    private fun letters(n: Int): String {
        var x = n; val sb = StringBuilder()
        while (x > 0) { x--; sb.insert(0, 'A' + x % 26); x /= 26 }
        return sb.toString()
    }

    private fun roman(n: Int): String {
        val v = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val s = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var x = n; val sb = StringBuilder()
        for (i in v.indices) while (x >= v[i]) { sb.append(s[i]); x -= v[i] }
        return sb.toString()
    }

    private fun changed() { cache = null; dirty = true }

    override fun setParagraphText(id: Int, text: String) {
        blocks()
        val p = paras.getOrNull(id) ?: return
        OoxmlText.setText(p, xmlSafe(text), NS_W, "w")
        changed()
    }

    private fun toggle(id: Int, flag: String) {
        blocks()
        val p = paras.getOrNull(id) ?: return
        val runs = OoxmlText.runs(p, NS_W).filter { r -> r.child(NS_W, "t") != null }
        if (runs.isEmpty()) return
        val allOn = runs.all { OoxmlText.isOn(it.child(NS_W, "rPr")?.child(NS_W, flag)) }
        runs.forEach { OoxmlText.setFlag(it, flag, !allOn) }
        changed()
    }

    override fun toggleBold(id: Int) = toggle(id, "b")
    override fun toggleItalic(id: Int) = toggle(id, "i")

    override fun addParagraphAfter(id: Int) {
        blocks()
        val p = paras.getOrNull(id) ?: return
        val np = docXml.createElementNS(NS_W, "w:p")
        p.child(NS_W, "pPr")?.let { old ->
            val copy = old.cloneNode(true) as Element
            copy.removeAll(NS_W, "sectPr"); copy.removeAll(NS_W, "rPr"); copy.removeAll(NS_W, "pPrChange")
            val style = copy.child(NS_W, "pStyle")?.attr(NS_W, "val").orEmpty()
            val name = styleNames[style] ?: style
            if (Regex("(?i)heading|title|überschrift").containsMatchIn(name)) copy.removeAll(NS_W, "pStyle")
            if (copy.hasChildNodes()) np.appendChild(copy)
        }
        p.parentNode.insertBefore(np, p.nextSibling)
        changed()
    }

    override fun deleteParagraph(id: Int) {
        blocks()
        val p = paras.getOrNull(id) ?: return
        val parent = p.parentNode as? Element
        val onlyInCell = parent != null && parent.isEl(NS_W, "tc") && parent.children(NS_W, "p").size <= 1
        val hasSection = p.child(NS_W, "pPr")?.child(NS_W, "sectPr") != null
        if (parent == null || onlyInCell || hasSection || (parent.isEl(NS_W, "body") && parent.children(NS_W, "p").size <= 1)) {
            OoxmlText.setText(p, "", NS_W, "w")
        } else parent.removeChild(p)
        changed()
    }

    override fun save(dst: File) {
        ZipRewrite.rewrite(file, dst, mapOf("word/document.xml" to OfficeXml.serialize(docXml, true)))
    }
}
