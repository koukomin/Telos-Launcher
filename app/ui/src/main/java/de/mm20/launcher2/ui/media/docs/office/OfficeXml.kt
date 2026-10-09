package de.mm20.launcher2.ui.media.docs.office

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal const val NS_XML = "http://www.w3.org/XML/1998/namespace"
internal const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
internal const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"

internal object OfficeXml {
    fun parse(bytes: ByteArray): Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    fun serialize(doc: Document, standalone: Boolean): ByteArray {
        val t = TransformerFactory.newInstance().newTransformer()
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        t.setOutputProperty(OutputKeys.INDENT, "no")
        if (standalone) doc.xmlStandalone = true
        val out = ByteArrayOutputStream()
        t.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }
}

/** Removes characters that are not allowed in XML 1.0 and normalises line breaks, so that user input can never produce a broken part. */
internal fun xmlSafe(s: String): String {
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '\r' -> { sb.append('\n'); if (i + 1 < s.length && s[i + 1] == '\n') i++ }
            c == '\n' || c == '\t' -> sb.append(c)
            c < ' ' || c == '\uFFFE' || c == '\uFFFF' -> {}
            Character.isHighSurrogate(c) -> if (i + 1 < s.length && Character.isLowSurrogate(s[i + 1])) { sb.append(c).append(s[i + 1]); i++ }
            Character.isLowSurrogate(c) -> {}
            else -> sb.append(c)
        }
        i++
    }
    return sb.toString()
}

private val plainNumber = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")

/** True when [s] can be stored as a number without changing how it reads (no leading zeros, at most 15 digits). */
internal fun isPlainNumber(s: String): Boolean {
    val t = s.trim()
    if (!plainNumber.matches(t)) return false
    val digits = t.trimStart('-')
    if (digits.length > 1 && digits[0] == '0' && digits[1].isDigit()) return false
    return digits.takeWhile { it.isDigit() }.length <= 15
}

internal const val MAX_BLOCKS = 20_000
internal const val MAX_SHEET_ROWS = 3_000
internal const val MAX_SHEET_COLS = 200

internal fun ZipFile.bytes(name: String): ByteArray? = getEntry(name)?.let { e -> getInputStream(e).use { it.readBytes() } }

internal fun Node.elements(): List<Element> {
    val out = ArrayList<Element>()
    var c = firstChild
    while (c != null) { if (c is Element) out += c; c = c.nextSibling }
    return out
}

internal fun Element.children(ns: String, local: String): List<Element> = elements().filter { it.namespaceURI == ns && it.localName == local }
internal fun Element.child(ns: String, local: String): Element? = elements().firstOrNull { it.namespaceURI == ns && it.localName == local }
internal fun Element.descendants(ns: String, local: String): List<Element> {
    val l = getElementsByTagNameNS(ns, local)
    return List(l.length) { l.item(it) as Element }
}
internal fun Element.attr(ns: String?, local: String): String? =
    if (hasAttributeNS(ns, local)) getAttributeNS(ns, local) else null
internal fun Element.isEl(ns: String, local: String) = namespaceURI == ns && localName == local

internal fun Element.removeAll(ns: String, local: String) { child(ns, local)?.let { removeChild(it) }; }

/** Resolves a relationship target against the folder of the part that owns the relationship. */
internal fun resolvePath(baseDir: String, target: String): String {
    if (target.startsWith("/")) return target.removePrefix("/")
    val parts = ArrayList<String>()
    (baseDir + target).split('/').forEach {
        when (it) { "", "." -> {}; ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1); else -> parts += it }
    }
    return parts.joinToString("/")
}

/** Reads a .rels part into id to resolved path. */
internal fun readRels(zip: ZipFile, relsEntry: String, baseDir: String): Map<String, String> {
    val bytes = zip.bytes(relsEntry) ?: return emptyMap()
    val doc = runCatching { OfficeXml.parse(bytes) }.getOrNull() ?: return emptyMap()
    val out = HashMap<String, String>()
    for (r in doc.documentElement.children(NS_PKG_REL, "Relationship")) {
        if (r.getAttribute("TargetMode") == "External") continue
        out[r.getAttribute("Id")] = resolvePath(baseDir, r.getAttribute("Target"))
    }
    return out
}

internal object ZipRewrite {
    /**
     * Writes a copy of [src] to [dst]. Entries in [replace] get new content, entries in [extra] are added, every other entry is copied unchanged.
     * The OpenDocument `mimetype` entry is written first and stored uncompressed.
     */
    fun rewrite(src: File, dst: File, replace: Map<String, ByteArray>, extra: Map<String, ByteArray> = emptyMap(), remove: Set<String> = emptySet()) {
        ZipFile(src).use { zip ->
            ZipOutputStream(FileOutputStream(dst).buffered()).use { out ->
                val entries = zip.entries().toList().sortedBy { if (it.name == "mimetype") 0 else 1 }
                val written = HashSet<String>()
                for (e in entries) {
                    if (e.name in remove || !written.add(e.name)) continue
                    val ne = ZipEntry(e.name)
                    if (e.time != -1L) ne.time = e.time
                    if (e.isDirectory) { out.putNextEntry(ne); out.closeEntry(); continue }
                    val data = replace[e.name]
                    val stored = e.method == ZipEntry.STORED || e.name == "mimetype"
                    if (stored) {
                        ne.method = ZipEntry.STORED
                        if (data != null) {
                            val crc = CRC32().apply { update(data) }
                            ne.size = data.size.toLong(); ne.compressedSize = data.size.toLong(); ne.crc = crc.value
                        } else {
                            val crc = CRC32()
                            zip.getInputStream(e).use { s -> val b = ByteArray(32768); while (true) { val n = s.read(b); if (n < 0) break; crc.update(b, 0, n) } }
                            ne.size = e.size; ne.compressedSize = e.size; ne.crc = crc.value
                        }
                    }
                    out.putNextEntry(ne)
                    if (data != null) out.write(data) else zip.getInputStream(e).use { it.copyTo(out) }
                    out.closeEntry()
                }
                for ((name, data) in extra) {
                    if (!written.add(name)) continue
                    out.putNextEntry(ZipEntry(name)); out.write(data); out.closeEntry()
                }
            }
        }
    }
}

internal fun File.copyTo2(dst: File) { FileInputStream(this).use { i -> FileOutputStream(dst).use { o -> i.copyTo(o) } } }
