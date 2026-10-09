package de.mm20.launcher2.ui.media.docs.office

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

/** PowerPoint presentation: text of the text boxes is edited in the slide XML, slides can be removed from the slide list. */
internal class PptxDoc(file: File) : OfficeDoc(file) {
    override val kind = OfficeKind.Slides

    private val z = zip!!
    private val pres: Document = OfficeXml.parse(z.bytes("ppt/presentation.xml") ?: error("no presentation"))
    private val presRels = readRels(z, "ppt/_rels/presentation.xml.rels", "ppt/")
    private val docs = HashMap<String, Document>()
    private val edited = HashSet<String>()
    private var presEdited = false
    private var aspect = 16f / 9f

    init {
        pres.documentElement.child(NS_P, "sldSz")?.let {
            val cx = it.getAttribute("cx").toFloatOrNull(); val cy = it.getAttribute("cy").toFloatOrNull()
            if (cx != null && cy != null && cy > 0) aspect = cx / cy
        }
    }

    override val slideAspect: Float get() = aspect

    private fun slidePaths(): List<String> =
        pres.documentElement.child(NS_P, "sldIdLst")?.children(NS_P, "sldId").orEmpty().mapNotNull { presRels[it.attr(NS_REL, "id").orEmpty()] }

    private fun slideDoc(path: String): Document? {
        docs[path]?.let { return it }
        val b = z.bytes(path) ?: return null
        return OfficeXml.parse(b).also { docs[path] = it }
    }

    private fun txBodies(d: Document): List<Element> {
        val l = d.getElementsByTagNameNS("*", "txBody")
        return List(l.length) { l.item(it) as Element }.filter { it.namespaceURI == NS_P || it.namespaceURI == NS_A }
    }

    private fun isTitle(body: Element): Boolean {
        val sp = body.parentNode as? Element ?: return false
        val ph = sp.child(NS_P, "nvSpPr")?.child(NS_P, "nvPr")?.child(NS_P, "ph") ?: return false
        val t = ph.getAttribute("type")
        return t == "title" || t == "ctrTitle"
    }

    private fun paraText(p: Element): String {
        val sb = StringBuilder()
        for (c in p.elements()) {
            if (c.namespaceURI != NS_A) continue
            when (c.localName) {
                "r", "fld" -> c.child(NS_A, "t")?.let { sb.append(it.textContent) }
                "br" -> sb.append('\n')
            }
        }
        return sb.toString()
    }

    private fun boxText(body: Element) = body.children(NS_A, "p").joinToString("\n") { paraText(it) }

    override fun slides(): List<OfficeSlide> = slidePaths().map { path ->
        val d = slideDoc(path)
        if (d == null) OfficeSlide(emptyList(), emptyList()) else {
            val boxes = txBodies(d).mapIndexed { i, b -> SlideBox(i, isTitle(b), boxText(b)) }
            val relsPath = path.substringBeforeLast('/') + "/_rels/" + path.substringAfterLast('/') + ".rels"
            val rels = readRels(z, relsPath, path.substringBeforeLast('/') + "/")
            val images = d.documentElement.descendants(NS_A, "blip").mapNotNull { it.attr(NS_REL, "embed")?.let { id -> rels[id] } }.distinct()
            OfficeSlide(boxes, images)
        }
    }

    override fun setBoxText(slide: Int, box: Int, text: String) {
        val path = slidePaths().getOrNull(slide) ?: return
        val d = slideDoc(path) ?: return
        val body = txBodies(d).getOrNull(box) ?: return
        val paras = body.children(NS_A, "p").toMutableList()
        val lines = text.split("\n").let { if (it.size == 1 && it[0].isEmpty()) listOf("") else it }
        // paragraphs that already hold a line break are treated as one unit, so compare with their own text
        for (i in lines.indices) {
            if (i < paras.size) {
                if (paraText(paras[i]) != lines[i]) OoxmlText.setText(paras[i], lines[i], NS_A, paras[i].prefix ?: "a")
            } else {
                val last = paras.last()
                val copy = last.cloneNode(true) as Element
                last.parentNode.insertBefore(copy, paras.last().nextSibling)
                OoxmlText.setText(copy, lines[i], NS_A, copy.prefix ?: "a")
                paras += copy
            }
        }
        for (i in paras.size - 1 downTo lines.size) {
            if (paras.size > 1) { paras[i].parentNode.removeChild(paras[i]); paras.removeAt(i) }
        }
        edited += path
        dirty = true
    }

    override fun deleteSlide(slide: Int) {
        val list = pres.documentElement.child(NS_P, "sldIdLst") ?: return
        val el = list.children(NS_P, "sldId").getOrNull(slide) ?: return
        val id = el.getAttribute("id")
        list.removeChild(el)
        // sections (extLst) refer to slide ids too
        if (id.isNotEmpty()) {
            val l = pres.getElementsByTagNameNS("*", "sldId")
            List(l.length) { l.item(it) as Element }.filter { it.getAttribute("id") == id && it.parentNode !== list }.forEach { it.parentNode.removeChild(it) }
        }
        presEdited = true
        dirty = true
    }

    override fun save(dst: File) {
        val replace = HashMap<String, ByteArray>()
        for (p in edited) docs[p]?.let { replace[p] = OfficeXml.serialize(it, true) }
        if (presEdited) replace["ppt/presentation.xml"] = OfficeXml.serialize(pres, true)
        ZipRewrite.rewrite(file, dst, replace)
    }
}
