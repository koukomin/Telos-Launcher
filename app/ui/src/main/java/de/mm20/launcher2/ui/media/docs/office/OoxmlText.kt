package de.mm20.launcher2.ui.media.docs.office

import org.w3c.dom.Element

internal const val NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
internal const val NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
internal const val NS_P = "http://schemas.openxmlformats.org/presentationml/2006/main"
internal const val NS_S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
internal const val NS_VML = "urn:schemas-microsoft-com:vml"

/** Text editing of w:p (Word) and a:p (DrawingML) paragraphs: both use the same element names for runs. */
internal object OoxmlText {
    private val containers = setOf("hyperlink", "ins", "smartTag", "sdt", "sdtContent", "customXml")

    /** All runs of the paragraph, also those inside hyperlinks and similar wrappers. */
    fun runs(p: Element, ns: String): List<Element> {
        val out = ArrayList<Element>()
        fun walk(e: Element) {
            for (c in e.elements()) {
                if (c.namespaceURI != ns) continue
                if (c.localName == "r") out += c else if (c.localName in containers) walk(c)
            }
        }
        walk(p)
        return out
    }

    private fun hasObject(run: Element): Boolean =
        run.elements().any { it.localName == "drawing" || it.localName == "pict" || it.localName == "object" || it.localName == "AlternateContent" }

    fun setText(p: Element, text: String, ns: String, prefix: String) {
        val doc = p.ownerDocument
        val word = ns == NS_W
        val all = runs(p, ns)
        val template = all.firstOrNull { r -> !hasObject(r) && r.child(ns, "t") != null } ?: all.firstOrNull { !hasObject(it) }
        for (r in all) {
            if (r !== template && !hasObject(r)) r.parentNode.removeChild(r)
        }
        if (!word) p.elements().filter { it.namespaceURI == ns && (it.localName == "br" || it.localName == "fld") }.forEach { p.removeChild(it) }
        // wrappers that lost all their runs
        p.elements().filter { it.namespaceURI == ns && it.localName in containers && it.elements().none { c -> c.localName == "r" || c.localName == "sdtContent" } }
            .forEach { p.removeChild(it) }
        val run = template ?: doc.createElementNS(ns, "$prefix:r").also { r ->
            val end = p.child(ns, "endParaRPr")
            if (end != null) p.insertBefore(r, end) else p.appendChild(r)
        }
        val keep = run.child(ns, "rPr")
        for (c in run.elements()) if (c !== keep) run.removeChild(c)
        val lines = xmlSafe(text).split("\n")
        if (word) {
            lines.forEachIndexed { li, line ->
                if (li > 0) run.appendChild(doc.createElementNS(ns, "$prefix:br"))
                line.split("\t").forEachIndexed { si, seg ->
                    if (si > 0) run.appendChild(doc.createElementNS(ns, "$prefix:tab"))
                    if (seg.isNotEmpty()) run.appendChild(textEl(doc, ns, prefix, seg, true))
                }
            }
        } else {
            // a:br has to sit between runs in DrawingML
            val parent = run.parentNode
            var anchor: org.w3c.dom.Node = run
            lines.forEachIndexed { i, line ->
                val target: Element
                if (i == 0) target = run else {
                    target = run.cloneNode(false) as Element
                    keep?.let { target.appendChild(it.cloneNode(true)) }
                    val br = doc.createElementNS(ns, "$prefix:br")
                    parent.insertBefore(br, anchor.nextSibling); anchor = br
                    parent.insertBefore(target, anchor.nextSibling); anchor = target
                }
                // a:r must always hold an a:t, also when the line is empty
                target.appendChild(textEl(doc, ns, prefix, line, false))
            }
        }
    }

    private fun textEl(doc: org.w3c.dom.Document, ns: String, prefix: String, s: String, preserve: Boolean): Element =
        doc.createElementNS(ns, "$prefix:t").also {
            if (preserve) it.setAttributeNS(NS_XML, "xml:space", "preserve")
            it.textContent = s
        }

    fun isOn(e: Element?): Boolean {
        if (e == null) return false
        val v = e.attr(NS_W, "val") ?: return true
        return v != "0" && v != "false" && v != "off"
    }

    /** Sets or clears bold ("b") or italic ("i") on a w:r run, keeping the schema order of w:rPr. */
    fun setFlag(run: Element, flag: String, on: Boolean) {
        val doc = run.ownerDocument
        var rPr = run.child(NS_W, "rPr")
        if (rPr == null) {
            rPr = doc.createElementNS(NS_W, "w:rPr")
            run.insertBefore(rPr, run.firstChild)
        }
        rPr.removeAll(NS_W, flag); rPr.removeAll(NS_W, flag + "Cs")
        val skip = if (flag == "b") setOf("rStyle", "rFonts") else setOf("rStyle", "rFonts", "b", "bCs")
        val el = doc.createElementNS(NS_W, "w:$flag")
        if (!on) el.setAttributeNS(NS_W, "w:val", "0")
        val before = rPr.elements().firstOrNull { it.localName !in skip }
        if (before != null) rPr.insertBefore(el, before) else rPr.appendChild(el)
    }
}
