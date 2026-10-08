package de.mm20.launcher2.ui.media.docs.office

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal reader for OLE2 / Compound File containers (the binary .doc, .xls and .ppt). */
internal class Cfb(private val b: ByteArray) {
    private val secShift: Int
    private val secSize: Int
    private val miniSize: Int
    private val miniCutoff: Int
    private val fat: IntArray
    private val miniFat: IntArray
    private class Entry(val name: String, val type: Int, val start: Int, val size: Long)
    private val entries = ArrayList<Entry>()
    private val miniStream: ByteArray

    private fun u16(o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(o: Int) = u16(o) or (u16(o + 2) shl 16)

    init {
        require(b.size > 512 && u32(0) == 0xE011CFD0.toInt() && u32(4) == 0xE11AB1A1.toInt()) { "not an OLE2 file" }
        secShift = u16(30); secSize = 1 shl secShift
        miniSize = 1 shl u16(32)
        miniCutoff = u32(56)
        val dirStart = u32(48)
        val miniFatStart = u32(60)
        var difat = u32(68)
        val fatSecs = ArrayList<Int>()
        for (i in 0 until 109) { val s = u32(76 + 4 * i); if (s >= 0) fatSecs += s }
        var guard = 0
        while (difat >= 0 && guard++ < 10000) {
            val o = off(difat)
            for (i in 0 until secSize / 4 - 1) { val s = u32(o + 4 * i); if (s >= 0) fatSecs += s }
            difat = u32(o + secSize - 4)
        }
        val f = ArrayList<Int>()
        for (s in fatSecs) { val o = off(s); if (o + secSize > b.size) break; for (i in 0 until secSize / 4) f += u32(o + 4 * i) }
        fat = f.toIntArray()
        miniFat = if (miniFatStart >= 0) readChain(miniFatStart, Long.MAX_VALUE).let { d -> IntArray(d.size / 4) { i -> (d[4 * i].toInt() and 0xFF) or ((d[4 * i + 1].toInt() and 0xFF) shl 8) or ((d[4 * i + 2].toInt() and 0xFF) shl 16) or ((d[4 * i + 3].toInt() and 0xFF) shl 24) } } else IntArray(0)
        val dir = readChain(dirStart, Long.MAX_VALUE)
        var i = 0
        while (i + 128 <= dir.size) {
            val len = ((dir[i + 64].toInt() and 0xFF) or ((dir[i + 65].toInt() and 0xFF) shl 8)).coerceIn(0, 64)
            val name = if (len >= 2) String(dir, i, len - 2, Charsets.UTF_16LE) else ""
            val type = dir[i + 66].toInt()
            fun d32(o: Int) = (dir[o].toInt() and 0xFF) or ((dir[o + 1].toInt() and 0xFF) shl 8) or ((dir[o + 2].toInt() and 0xFF) shl 16) or ((dir[o + 3].toInt() and 0xFF) shl 24)
            entries += Entry(name, type, d32(i + 116), d32(i + 120).toLong() and 0xFFFFFFFFL)
            i += 128
        }
        val root = entries.firstOrNull { it.type == 5 }
        miniStream = if (root != null && root.start >= 0) readChain(root.start, root.size) else ByteArray(0)
    }

    private fun off(sector: Int): Int = (sector + 1) * secSize

    private fun readChain(start: Int, size: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var s = start
        var guard = 0
        while (s >= 0 && guard++ < 4_000_000 && out.size() < size) {
            val o = off(s)
            if (o < 0 || o + secSize > b.size) break
            out.write(b, o, secSize)
            s = fat.getOrElse(s) { -2 }
        }
        val all = out.toByteArray()
        return if (size < all.size) all.copyOf(size.toInt()) else all
    }

    fun has(name: String) = entries.any { it.type == 2 && it.name.equals(name, true) }

    fun stream(name: String): ByteArray? {
        val e = entries.firstOrNull { it.type == 2 && it.name.equals(name, true) } ?: return null
        if (e.size >= miniCutoff) return readChain(e.start, e.size)
        val out = ByteArrayOutputStream()
        var s = e.start
        var guard = 0
        while (s >= 0 && guard++ < 1_000_000 && out.size() < e.size) {
            val o = s * miniSize
            if (o + miniSize > miniStream.size) break
            out.write(miniStream, o, miniSize)
            s = miniFat.getOrElse(s) { -2 }
        }
        val all = out.toByteArray()
        return if (e.size < all.size) all.copyOf(e.size.toInt()) else all
    }
}

private fun ByteArray.u8(o: Int) = this[o].toInt() and 0xFF
private fun ByteArray.u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
private fun ByteArray.u32(o: Int) = u16(o) or (u16(o + 2) shl 16)

internal object LegacyReaders {

    fun word(cfb: Cfb): List<String> {
        val wd = cfb.stream("WordDocument") ?: error("no WordDocument")
        require(wd.u16(0) == 0xA5EC) { "unsupported Word version" }
        val flags = wd.u16(0x0A)
        val table = cfb.stream(if (flags and 0x200 != 0) "1Table" else "0Table") ?: error("no table stream")
        val ccpText = wd.u32(0x4C)
        val fcClx = wd.u32(0x1A2)
        val lcbClx = wd.u32(0x1A6)
        require(lcbClx > 0 && fcClx >= 0 && fcClx + lcbClx <= table.size) { "no text table" }
        var pos = fcClx
        while (table.u8(pos) == 1) pos += 3 + table.u16(pos + 1)
        require(table.u8(pos) == 2)
        val lcb = table.u32(pos + 1)
        val plc = pos + 5
        val n = (lcb - 4) / 12
        val sb = StringBuilder()
        for (i in 0 until n) {
            val cpStart = table.u32(plc + 4 * i)
            val cpEnd = table.u32(plc + 4 * (i + 1))
            val pcd = plc + 4 * (n + 1) + 8 * i
            val fc = table.u32(pcd + 2)
            val len = cpEnd - cpStart
            if (len <= 0) continue
            if (fc and 0x40000000 != 0) {
                val o = (fc and 0x3FFFFFFF) / 2
                if (o + len <= wd.size) sb.append(String(wd, o, len, charset("windows-1252")))
            } else if (fc + len * 2 <= wd.size) sb.append(String(wd, fc, len * 2, Charsets.UTF_16LE))
            if (sb.length >= ccpText) break
        }
        var raw = sb.toString()
        if (ccpText in 1 until raw.length) raw = raw.substring(0, ccpText)
        // fields: keep the shown result, drop the instruction
        val out = StringBuilder()
        var depth = 0
        val inInstruction = ArrayList<Boolean>()
        for (c in raw) {
            when (c) {
                '\u0013' -> { inInstruction.add(true); depth++ }
                '\u0014' -> if (inInstruction.isNotEmpty()) inInstruction[inInstruction.size - 1] = false
                '\u0015' -> if (inInstruction.isNotEmpty()) inInstruction.removeAt(inInstruction.size - 1)
                else -> if (inInstruction.none { it }) when (c) {
                    '\u0007' -> out.append('\t')
                    '\u000B', '\u000C' -> out.append('\n')
                    '\u001E' -> out.append('-')
                    '\u0001', '\u0008', '\u0002', '\u0005' -> {}
                    else -> out.append(c)
                }
            }
        }
        return out.toString().split('\r').map { it.trimEnd('\t').trimEnd() }.let { l ->
            var x = l
            while (x.isNotEmpty() && x.last().isEmpty()) x = x.dropLast(1)
            x
        }
    }

    private fun rk(v: Int): String {
        val d: Double = if (v and 2 != 0) (v shr 2).toDouble() else java.lang.Double.longBitsToDouble((v.toLong() and 0xFFFFFFFCL) shl 32)
        return num(if (v and 1 != 0) d / 100.0 else d)
    }

    private fun num(d: Double): String =
        if (d == Math.rint(d) && Math.abs(d) < 1e15) d.toLong().toString() else java.math.BigDecimal(d).round(java.math.MathContext(15)).stripTrailingZeros().toPlainString()

    private class Sst(val segs: List<ByteArray>) {
        var seg = 0; var pos = 0
        fun more(): Boolean { while (seg < segs.size && pos >= segs[seg].size) { seg++; pos = 0 }; return seg < segs.size }
        fun u8(): Int = if (more()) segs[seg][pos++].toInt() and 0xFF else 0
        fun readString(): String {
            val cch = u8() or (u8() shl 8)
            val flags = u8()
            val runs = if (flags and 8 != 0) u8() or (u8() shl 8) else 0
            val ext = if (flags and 4 != 0) u8() or (u8() shl 8) or (u8() shl 16) or (u8() shl 24) else 0
            var wide = flags and 1 != 0
            val sb = StringBuilder()
            var read = 0
            while (read < cch) {
                if (seg < segs.size && pos >= segs[seg].size) {
                    if (!more()) break
                    wide = u8() and 1 != 0
                    continue
                }
                if (!more()) break
                val bpc = if (wide) 2 else 1
                val avail = (segs[seg].size - pos) / bpc
                val n = minOf(cch - read, avail)
                if (n <= 0) { pos++; continue }
                for (k in 0 until n) {
                    if (wide) { val lo = u8(); val hi = u8(); sb.append((lo or (hi shl 8)).toChar()) } else sb.append(u8().toChar())
                }
                read += n
            }
            repeat(runs * 4 + ext.coerceAtLeast(0).coerceAtMost(1_000_000)) { u8() }
            return sb.toString()
        }
    }

    fun excel(cfb: Cfb): List<OfficeSheet> {
        val wb = cfb.stream("Workbook") ?: cfb.stream("Book") ?: error("no workbook stream")
        val sst = ArrayList<String>()
        val names = ArrayList<String>()
        val sheets = ArrayList<HashMap<Long, String>>()
        var cur: HashMap<Long, String>? = null
        var pendingFormula = -1L
        var pos = 0
        fun put(r: Int, c: Int, v: String) { cur?.put(OfficeSheet.key(r, c), v) }
        while (pos + 4 <= wb.size) {
            val type = wb.u16(pos); val len = wb.u16(pos + 2)
            val d = pos + 4
            if (d + len > wb.size) break
            when (type) {
                0x809 -> {
                    val dt = wb.u16(d + 2)
                    if (dt == 0x10) { cur = HashMap(); sheets += cur!! } else if (dt != 0x05) cur = null
                }
                0xFC -> {
                    val segs = arrayListOf(wb.copyOfRange(d + 8, d + len))
                    var p = d + len
                    while (p + 4 <= wb.size && wb.u16(p) == 0x3C) { val l = wb.u16(p + 2); if (p + 4 + l > wb.size) break; segs += wb.copyOfRange(p + 4, p + 4 + l); p += 4 + l }
                    val count = wb.u32(d + 4)
                    val r = Sst(segs)
                    for (i in 0 until count.coerceAtMost(500_000)) { if (!r.more()) break; sst += r.readString() }
                }
                0x85 -> {
                    val cch = wb.u8(d + 6); val wide = wb.u8(d + 7) and 1 != 0
                    names += if (wide) String(wb, d + 8, cch * 2, Charsets.UTF_16LE) else String(wb, d + 8, cch, charset("windows-1252"))
                }
                0xFD -> put(wb.u16(d), wb.u16(d + 2), sst.getOrElse(wb.u32(d + 6)) { "" })
                0x204 -> {
                    val cch = wb.u16(d + 6); val wide = wb.u8(d + 8) and 1 != 0
                    if (d + 9 + (if (wide) cch * 2 else cch) <= wb.size) put(wb.u16(d), wb.u16(d + 2), if (wide) String(wb, d + 9, cch * 2, Charsets.UTF_16LE) else String(wb, d + 9, cch, charset("windows-1252")))
                }
                0x203 -> put(wb.u16(d), wb.u16(d + 2), num(java.lang.Double.longBitsToDouble((wb.u32(d + 6).toLong() and 0xFFFFFFFFL) or (wb.u32(d + 10).toLong() shl 32))))
                0x27E -> put(wb.u16(d), wb.u16(d + 2), rk(wb.u32(d + 6)))
                0xBD -> {
                    val row = wb.u16(d); val first = wb.u16(d + 2)
                    val n = (len - 6) / 6
                    for (i in 0 until n) put(row, first + i, rk(wb.u32(d + 4 + 6 * i + 2)))
                }
                0x205 -> if (wb.u8(d + 7) == 0) put(wb.u16(d), wb.u16(d + 2), if (wb.u8(d + 6) != 0) "TRUE" else "FALSE")
                0x06 -> {
                    val row = wb.u16(d); val col = wb.u16(d + 2)
                    if (wb.u16(d + 12) == 0xFFFF) {
                        when (wb.u8(d + 6)) {
                            0 -> pendingFormula = OfficeSheet.key(row, col)
                            1 -> put(row, col, if (wb.u8(d + 8) != 0) "TRUE" else "FALSE")
                        }
                    } else put(row, col, num(java.lang.Double.longBitsToDouble((wb.u32(d + 6).toLong() and 0xFFFFFFFFL) or (wb.u32(d + 10).toLong() shl 32))))
                }
                0x207 -> if (pendingFormula >= 0) {
                    val cch = wb.u16(d); val wide = wb.u8(d + 2) and 1 != 0
                    if (d + 3 + (if (wide) cch * 2 else cch) <= wb.size)
                        cur?.put(pendingFormula, if (wide) String(wb, d + 3, cch * 2, Charsets.UTF_16LE) else String(wb, d + 3, cch, charset("windows-1252")))
                    pendingFormula = -1
                }
            }
            pos = d + len
        }
        return sheets.mapIndexed { i, m ->
            var maxR = -1; var maxC = -1
            for (k in m.keys) { val r = (k shr 20).toInt(); val c = (k and 0xFFFFF).toInt(); if (r > maxR) maxR = r; if (c > maxC) maxC = c }
            OfficeSheet(names.getOrNull(i) ?: "Sheet ${i + 1}", maxR + 1, maxC + 1, m, emptyMap(), false)
        }
    }

    fun powerpoint(cfb: Cfb): List<OfficeSlide> {
        val ds = cfb.stream("PowerPoint Document") ?: error("no PowerPoint stream")
        val slides = ArrayList<MutableList<SlideBox>>()
        var header = -1
        fun parse(start: Int, end: Int, ctx: Int) {
            var pos = start
            while (pos + 8 <= end) {
                val verInst = ds.u16(pos)
                val ver = verInst and 0xF; val inst = verInst ushr 4
                val type = ds.u16(pos + 2); val len = ds.u32(pos + 4)
                val bodyEnd = (pos + 8 + len.toLong()).coerceAtMost(end.toLong()).toInt()
                if (len < 0) break
                if (ver == 0xF) parse(pos + 8, bodyEnd, if (type == 0x0FF0) inst else ctx)
                else if (ctx == 0) {
                    when (type) {
                        0x03F3 -> { slides += ArrayList(); header = -1 }
                        0x0F9F -> if (pos + 12 <= end) header = ds.u32(pos + 8)
                        0x0FA0, 0x0FA8 -> if (slides.isNotEmpty()) {
                            val text = if (type == 0x0FA0) String(ds, pos + 8, (bodyEnd - pos - 8) and 1.inv(), Charsets.UTF_16LE) else String(ds, pos + 8, bodyEnd - pos - 8, charset("windows-1252"))
                            val clean = text.replace('\r', '\n').replace('\u000B', '\n').trim('\n')
                            slides.last() += SlideBox(slides.last().size, header == 0 || header == 6, clean)
                        }
                    }
                }
                pos = bodyEnd
            }
        }
        parse(0, ds.size, -1)
        return slides.map { OfficeSlide(it, emptyList()) }
    }
}

/** A legacy binary Office file: shown read-only, can be converted to the matching OOXML format. */
internal class LegacyDoc(file: File, val ext: String) : OfficeDoc(file) {
    override val kind = when (ext) { "xls" -> OfficeKind.Sheet; "ppt" -> OfficeKind.Slides; else -> OfficeKind.Text }
    override val readOnly: Boolean get() = true

    private val paragraphs: List<String>
    private val sheetList: List<OfficeSheet>
    private val slideList: List<OfficeSlide>

    init {
        val cfb = Cfb(file.readBytes())
        paragraphs = if (ext == "doc") LegacyReaders.word(cfb) else emptyList()
        sheetList = if (ext == "xls") LegacyReaders.excel(cfb) else emptyList()
        slideList = if (ext == "ppt") LegacyReaders.powerpoint(cfb) else emptyList()
    }

    override fun blocks(): List<OfficeBlock> = paragraphs.map { OfficeBlock.Para(-1, listOf(OfficeRun(it))) }
    override fun sheets() = sheetList
    override fun slides() = slideList

    val targetExt: String get() = when (ext) { "xls" -> "xlsx"; "ppt" -> "pptx"; else -> "docx" }

    /** Writes a new OOXML package with the text, cell values and slide texts. Nothing else of the original is carried over. */
    fun convertTo(dst: File) {
        when (ext) {
            "doc" -> OoxmlWriter.docx(dst, paragraphs)
            "xls" -> OoxmlWriter.xlsx(dst, sheetList)
            else -> OoxmlWriter.pptx(dst, slideList)
        }
    }
}

internal object OoxmlWriter {
    private fun esc(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c == '\t' || c == '\n' || c == '\r' || (c >= ' ' && c != '\uFFFE' && c != '\uFFFF') -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun zip(dst: File, parts: List<Pair<String, String>>) {
        ZipOutputStream(dst.outputStream().buffered()).use { z ->
            for ((n, c) in parts) { z.putNextEntry(ZipEntry(n)); z.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n$c").toByteArray()); z.closeEntry() }
        }
    }

    private const val CT = "http://schemas.openxmlformats.org/package/2006/content-types"
    private const val PR = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun rels(vararg r: Triple<String, String, String>) =
        "<Relationships xmlns=\"$PR\">" + r.joinToString("") { "<Relationship Id=\"${it.first}\" Type=\"$R/${it.second}\" Target=\"${it.third}\"/>" } + "</Relationships>"

    fun docx(dst: File, paras: List<String>) {
        val body = (if (paras.isEmpty()) listOf("") else paras).joinToString("") { p ->
            val runs = p.split("\n").joinToString("<w:r><w:br/></w:r>") { line ->
                line.split("\t").joinToString("<w:r><w:tab/></w:r>") { "<w:r><w:t xml:space=\"preserve\">${esc(it)}</w:t></w:r>" }
            }
            "<w:p>$runs</w:p>"
        }
        zip(dst, listOf(
            "[Content_Types].xml" to "<Types xmlns=\"$CT\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
            "_rels/.rels" to rels(Triple("rId1", "officeDocument", "word/document.xml")),
            "word/document.xml" to "<w:document xmlns:w=\"$NS_W\"><w:body>$body<w:sectPr/></w:body></w:document>",
        ))
    }

    fun xlsx(dst: File, sheets: List<OfficeSheet>) {
        val list = sheets.ifEmpty { listOf(OfficeSheet("Sheet1", 0, 0, emptyMap(), emptyMap(), true)) }
        val parts = ArrayList<Pair<String, String>>()
        parts += "[Content_Types].xml" to "<Types xmlns=\"$CT\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            list.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } + "</Types>"
        parts += "_rels/.rels" to rels(Triple("rId1", "officeDocument", "xl/workbook.xml"))
        parts += "xl/workbook.xml" to "<workbook xmlns=\"$NS_S\" xmlns:r=\"$R\"><sheets>" +
            list.mapIndexed { i, s -> "<sheet name=\"${esc(s.name.take(31).replace(Regex("[\\\\/?*\\[\\]:]"), "_").ifEmpty { "Sheet${i + 1}" })}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") + "</sheets></workbook>"
        parts += "xl/_rels/workbook.xml.rels" to rels(*list.indices.map { Triple("rId${it + 1}", "worksheet", "worksheets/sheet${it + 1}.xml") }.toTypedArray())
        val number = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")
        list.forEachIndexed { si, s ->
            val rows = StringBuilder()
            for (r in 0 until s.rows) {
                val cells = StringBuilder()
                for (c in 0 until s.cols) {
                    val v = s.value(r, c)
                    if (v.isEmpty()) continue
                    val ref = colName(c) + (r + 1)
                    if (number.matches(v)) cells.append("<c r=\"$ref\"><v>$v</v></c>")
                    else cells.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>")
                }
                if (cells.isNotEmpty()) rows.append("<row r=\"${r + 1}\">$cells</row>")
            }
            parts += "xl/worksheets/sheet${si + 1}.xml" to "<worksheet xmlns=\"$NS_S\"><sheetData>$rows</sheetData></worksheet>"
        }
        zip(dst, parts)
    }

    fun pptx(dst: File, slides: List<OfficeSlide>) {
        val list = slides.ifEmpty { listOf(OfficeSlide(emptyList(), emptyList())) }
        val ns = "xmlns:a=\"$NS_A\" xmlns:r=\"$R\" xmlns:p=\"$NS_P\""
        val parts = ArrayList<Pair<String, String>>()
        val ctPrefix = "application/vnd.openxmlformats-officedocument"
        parts += "[Content_Types].xml" to "<Types xmlns=\"$CT\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/ppt/presentation.xml\" ContentType=\"$ctPrefix.presentationml.presentation.main+xml\"/>" +
            "<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"$ctPrefix.presentationml.slideMaster+xml\"/>" +
            "<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"$ctPrefix.presentationml.slideLayout+xml\"/>" +
            "<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"$ctPrefix.theme+xml\"/>" +
            list.indices.joinToString("") { "<Override PartName=\"/ppt/slides/slide${it + 1}.xml\" ContentType=\"$ctPrefix.presentationml.slide+xml\"/>" } + "</Types>"
        parts += "_rels/.rels" to rels(Triple("rId1", "officeDocument", "ppt/presentation.xml"))
        parts += "ppt/presentation.xml" to "<p:presentation $ns><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst><p:sldIdLst>" +
            list.indices.joinToString("") { "<p:sldId id=\"${256 + it}\" r:id=\"rId${it + 3}\"/>" } + "</p:sldIdLst><p:sldSz cx=\"12192000\" cy=\"6858000\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>"
        parts += "ppt/_rels/presentation.xml.rels" to rels(
            Triple("rId1", "slideMaster", "slideMasters/slideMaster1.xml"), Triple("rId2", "theme", "theme/theme1.xml"),
            *list.indices.map { Triple("rId${it + 3}", "slide", "slides/slide${it + 1}.xml") }.toTypedArray())
        val group = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>"
        parts += "ppt/slideMasters/slideMaster1.xml" to "<p:sldMaster $ns><p:cSld><p:spTree>$group</p:spTree></p:cSld><p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/><p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst></p:sldMaster>"
        parts += "ppt/slideMasters/_rels/slideMaster1.xml.rels" to rels(Triple("rId1", "slideLayout", "../slideLayouts/slideLayout1.xml"), Triple("rId2", "theme", "../theme/theme1.xml"))
        parts += "ppt/slideLayouts/slideLayout1.xml" to "<p:sldLayout $ns type=\"blank\"><p:cSld name=\"Blank\"><p:spTree>$group</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"
        parts += "ppt/slideLayouts/_rels/slideLayout1.xml.rels" to rels(Triple("rId1", "slideMaster", "../slideMasters/slideMaster1.xml"))
        val fill = "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>"
        val ln = "<a:ln w=\"6350\">$fill</a:ln>"
        parts += "ppt/theme/theme1.xml" to "<a:theme xmlns:a=\"$NS_A\" name=\"Telos\"><a:themeElements><a:clrScheme name=\"Telos\"><a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1><a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"44546A\"/></a:dk2><a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2>" +
            "<a:accent1><a:srgbClr val=\"4472C4\"/></a:accent1><a:accent2><a:srgbClr val=\"ED7D31\"/></a:accent2><a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3><a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4><a:accent5><a:srgbClr val=\"5B9BD5\"/></a:accent5><a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6><a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink><a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink></a:clrScheme>" +
            "<a:fontScheme name=\"Telos\"><a:majorFont><a:latin typeface=\"Calibri\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont><a:minorFont><a:latin typeface=\"Calibri\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme>" +
            "<a:fmtScheme name=\"Telos\"><a:fillStyleLst>$fill$fill$fill</a:fillStyleLst><a:lnStyleLst>$ln$ln$ln</a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst>$fill$fill$fill</a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>"
        list.forEachIndexed { i, s ->
            val title = s.boxes.firstOrNull { it.title }
            val others = s.boxes.filter { it !== title && it.text.isNotBlank() }
            fun sp(id: Int, name: String, ph: String, x: Int, y: Int, cx: Int, cy: Int, text: String) =
                "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name\"/><p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"$ph\"${if (ph == "body") " idx=\"1\"" else ""}/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm></p:spPr><p:txBody><a:bodyPr/><a:lstStyle/>" +
                    text.split("\n").joinToString("") { "<a:p><a:r><a:rPr lang=\"en-US\"${if (ph == "title") " sz=\"3600\" b=\"1\"" else " sz=\"2000\""}/><a:t>${esc(it)}</a:t></a:r></a:p>" } + "</p:txBody></p:sp>"
            val shapes = StringBuilder()
            shapes.append(sp(2, "Title", "title", 609600, 365125, 10972800, 1000000, title?.text.orEmpty()))
            shapes.append(sp(3, "Content", "body", 609600, 1600000, 10972800, 4600000, others.joinToString("\n") { it.text }))
            parts += "ppt/slides/slide${i + 1}.xml" to "<p:sld $ns><p:cSld><p:spTree>$group$shapes</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
            parts += "ppt/slides/_rels/slide${i + 1}.xml.rels" to rels(Triple("rId1", "slideLayout", "../slideLayouts/slideLayout1.xml"))
        }
        zip(dst, parts)
    }
}

internal object OfficeDocs {
    val editable = setOf("docx", "xlsx", "pptx", "odt", "ods", "odp")
    val legacy = setOf("doc", "xls", "ppt")

    fun open(file: File, name: String): OfficeDoc {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "docx" -> DocxDoc(file)
            "xlsx" -> XlsxDoc(file)
            "pptx" -> PptxDoc(file)
            "odt" -> OdtDoc(file)
            "ods" -> OdsDoc(file)
            "odp" -> OdpDoc(file)
            "doc", "xls", "ppt" -> LegacyDoc(file, ext)
            else -> error("unsupported")
        }
    }
}
