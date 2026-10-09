package de.mm20.launcher2.ui.media.docs.office

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

internal fun colName(col: Int): String {
    var n = col + 1; val sb = StringBuilder()
    while (n > 0) { n--; sb.insert(0, 'A' + n % 26); n /= 26 }
    return sb.toString()
}

internal fun colIndex(ref: String): Int {
    var n = 0
    for (c in ref) { if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1) else if (c in 'a'..'z') n = n * 26 + (c - 'a' + 1) else break }
    return n - 1
}

/** Excel workbook: cell values are changed in the sheet XML. Formulas stay formulas; strings are written as inline strings so the shared string table is not touched. */
internal class XlsxDoc(file: File) : OfficeDoc(file) {
    override val kind = OfficeKind.Sheet

    private val z = zip!!
    private val workbook: Document = OfficeXml.parse(z.bytes("xl/workbook.xml") ?: error("no workbook"))
    private val shared = ArrayList<String>()
    private val names = ArrayList<String>()
    private val paths = ArrayList<String>()
    private val docs = HashMap<Int, Document>()
    private val edited = HashSet<Int>()
    private var cache: List<OfficeSheet>? = null

    init {
        z.bytes("xl/sharedStrings.xml")?.let { b ->
            val root = OfficeXml.parse(b).documentElement
            for (si in root.children(NS_S, "si")) {
                val sb = StringBuilder()
                for (c in si.elements()) {
                    if (c.isEl(NS_S, "t")) sb.append(c.textContent)
                    else if (c.isEl(NS_S, "r")) c.child(NS_S, "t")?.let { sb.append(it.textContent) }
                }
                shared += sb.toString()
            }
        }
        val rels = readRels(z, "xl/_rels/workbook.xml.rels", "xl/")
        val sheets = workbook.documentElement.child(NS_S, "sheets")?.children(NS_S, "sheet").orEmpty()
        for ((i, s) in sheets.withIndex()) {
            names += s.getAttribute("name").ifEmpty { "Sheet ${i + 1}" }
            paths += rels[s.attr(NS_REL, "id").orEmpty()].orEmpty()
        }
    }

    private fun sheetDoc(i: Int): Document? {
        docs[i]?.let { return it }
        val path = paths.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: return null
        val bytes = z.bytes(path) ?: return null
        return OfficeXml.parse(bytes).also { docs[i] = it }
    }

    override fun sheets(): List<OfficeSheet> {
        cache?.let { return it }
        val out = names.indices.map { i ->
            val values = HashMap<Long, String>()
            val formulas = HashMap<Long, String>()
            var maxRow = -1; var maxCol = -1
            val sheetData = sheetDoc(i)?.documentElement?.child(NS_S, "sheetData")
            var rowNo = -1
            for (row in sheetData?.children(NS_S, "row").orEmpty()) {
                rowNo = row.getAttribute("r").toIntOrNull()?.minus(1) ?: (rowNo + 1)
                var colNo = -1
                for (c in row.children(NS_S, "c")) {
                    val ref = c.getAttribute("r")
                    colNo = if (ref.isNotEmpty()) colIndex(ref) else colNo + 1
                    val t = c.getAttribute("t")
                    val v = c.child(NS_S, "v")?.textContent.orEmpty()
                    val text = when (t) {
                        "s" -> shared.getOrNull(v.trim().toIntOrNull() ?: -1).orEmpty()
                        "inlineStr" -> c.child(NS_S, "is")?.descendants(NS_S, "t")?.joinToString("") { it.textContent }.orEmpty()
                        "b" -> if (v.trim() == "1") "TRUE" else "FALSE"
                        else -> v
                    }
                    val f = c.child(NS_S, "f")
                    val fText = f?.textContent.orEmpty()
                    if (text.isEmpty() && fText.isEmpty()) continue
                    val k = OfficeSheet.key(rowNo, colNo)
                    values[k] = text
                    if (fText.isNotBlank()) formulas[k] = "=$fText"
                    if (rowNo > maxRow) maxRow = rowNo
                    if (colNo > maxCol) maxCol = colNo
                }
            }
            OfficeSheet(names[i], maxRow + 1, maxCol + 1, values, formulas, true)
        }
        cache = out
        return out
    }

    override fun setCell(sheet: Int, row: Int, col: Int, text: String) {
        val d = sheetDoc(sheet) ?: return
        val sheetData = d.documentElement.child(NS_S, "sheetData") ?: return
        val rowEl = findOrCreate(sheetData, NS_S, "row", row + 1, d) { it.getAttribute("r").toIntOrNull() ?: 0 }
        rowEl.removeAttribute("spans")
        val cell = findOrCreate(rowEl, NS_S, "c", col + 1, d) { c -> c.getAttribute("r").let { if (it.isEmpty()) 0 else colIndex(it) + 1 } }
        cell.setAttribute("r", colName(col) + (row + 1))
        // current content goes
        cell.removeAttribute("t")
        for (n in listOf("f", "v", "is")) cell.removeAll(NS_S, n)
        val value = text
        val prefix = cell.prefix?.let { "$it:" } ?: ""
        if (value.length > 1 && value.startsWith("=")) {
            val f = d.createElementNS(NS_S, prefix + "f")
            f.textContent = value.substring(1)
            cell.insertBefore(f, cell.firstChild)
        } else if (value.isNotEmpty()) {
            if (Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""").matches(value.trim())) {
                val v = d.createElementNS(NS_S, prefix + "v"); v.textContent = value.trim(); cell.appendChild(v)
            } else {
                cell.setAttribute("t", "inlineStr")
                val isEl = d.createElementNS(NS_S, prefix + "is")
                val t = d.createElementNS(NS_S, prefix + "t")
                t.setAttributeNS(NS_XML, "xml:space", "preserve")
                t.textContent = value
                isEl.appendChild(t); cell.insertBefore(isEl, cell.firstChild)
            }
        }
        edited += sheet
        cache = null
        dirty = true
    }

    private fun findOrCreate(parent: Element, ns: String, local: String, number: Int, d: Document, num: (Element) -> Int): Element {
        val prefix = parent.prefix?.let { "$it:" } ?: ""
        var before: Element? = null
        for (e in parent.children(ns, local)) {
            val n = num(e)
            if (n == number) return e
            if (n > number) { before = e; break }
        }
        val created = d.createElementNS(ns, prefix + local)
        if (local == "row") created.setAttribute("r", number.toString())
        parent.insertBefore(created, before)
        return created
    }

    override fun save(dst: File) {
        val replace = HashMap<String, ByteArray>()
        for (i in edited) {
            val d = docs[i] ?: continue
            // keep the dimension in line with the cells
            var maxRow = 0; var maxCol = 0
            d.documentElement.child(NS_S, "sheetData")?.children(NS_S, "row")?.forEach { r ->
                r.getAttribute("r").toIntOrNull()?.let { if (it > maxRow) maxRow = it }
                r.children(NS_S, "c").forEach { c -> val ci = colIndex(c.getAttribute("r")) + 1; if (ci > maxCol) maxCol = ci }
            }
            d.documentElement.child(NS_S, "dimension")?.let { if (maxRow > 0 && maxCol > 0) it.setAttribute("ref", "A1:" + colName(maxCol - 1) + maxRow) }
            replace[paths[i]] = OfficeXml.serialize(d, true)
        }
        if (edited.isNotEmpty()) {
            // formulas that depend on edited cells are recalculated when the file is opened
            val root = workbook.documentElement
            val calc = root.child(NS_S, "calcPr")
            if (calc != null) calc.setAttribute("fullCalcOnLoad", "1")
            else {
                val el = workbook.createElementNS(NS_S, (root.prefix?.let { "$it:" } ?: "") + "calcPr")
                el.setAttribute("fullCalcOnLoad", "1")
                val after = root.elements().lastOrNull { it.localName in setOf("sheets", "functionGroups", "externalReferences", "definedNames") }
                root.insertBefore(el, after?.nextSibling)
            }
            replace["xl/workbook.xml"] = OfficeXml.serialize(workbook, true)
        }
        ZipRewrite.rewrite(file, dst, replace)
    }
}
