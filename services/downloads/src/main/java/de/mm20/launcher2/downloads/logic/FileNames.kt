package de.mm20.launcher2.downloads.logic

import java.net.URI
import java.net.URLDecoder

/** File name rules: from Content-Disposition, from the address, and making a name safe. */
object FileNames {

    /** The file name of a Content-Disposition header (filename* wins over filename), null if there is none */
    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val params = splitParams(header)
        params["filename*"]?.let { raw ->
            decodeExtValue(raw)?.let { return sanitize(it).ifBlank { null } }
        }
        params["filename"]?.let { raw ->
            val v = unquote(raw)
            // some servers send percent encoded names in the plain parameter
            val decoded = if (v.contains('%')) runCatching { URLDecoder.decode(v.replace("+", "%2B"), "UTF-8") }.getOrDefault(v) else v
            return sanitize(decoded).ifBlank { null }
        }
        return null
    }

    /** Last path segment of the address, decoded, null if there is none */
    fun fromUrl(url: String): String? {
        val path = try {
            URI(url.trim()).rawPath
        } catch (e: Exception) {
            url.substringBefore('?').substringBefore('#').substringAfter("://").substringAfter('/', "")
        } ?: return null
        val last = path.trimEnd('/').substringAfterLast('/')
        if (last.isBlank()) return null
        val decoded = runCatching { URLDecoder.decode(last.replace("+", "%2B"), "UTF-8") }.getOrDefault(last)
        return sanitize(decoded).ifBlank { null }
    }

    /**
     * The name for a download: Content-Disposition, then the address, then "download". If the name has no
     * extension the one of the [mimeType] is added.
     */
    fun resolve(contentDisposition: String?, url: String, mimeType: String?): String {
        val base = fromContentDisposition(contentDisposition) ?: fromUrl(url) ?: "download"
        if (base.contains('.') && !base.endsWith(".")) return base
        val ext = MimeTypes.extensionFor(mimeType) ?: return base
        return "$base.$ext"
    }

    /** Removes path separators, control characters and characters that file systems refuse */
    fun sanitize(name: String): String {
        var n = name.substringAfterLast('/').substringAfterLast('\\')
        n = n.map { c -> if (c.code < 32 || c in "\"*:<>?|") '_' else c }.joinToString("")
        n = n.trim().trimStart('.').trimEnd('.', ' ')
        if (n.length > 200) {
            val ext = n.substringAfterLast('.', "")
            n = if (ext.isNotEmpty() && ext.length <= 10) n.take(200 - ext.length - 1) + "." + ext else n.take(200)
        }
        return n
    }

    /** Adds " (1)", " (2)" ... before the extension until [exists] is false */
    fun unique(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val (stem, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var i = 1
        while (true) {
            val candidate = "$stem ($i)$ext"
            if (!exists(candidate)) return candidate
            i++
        }
    }

    private fun splitParams(header: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var i = 0
        val parts = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        while (i < header.length) {
            val c = header[i]
            if (c == '"' && (i == 0 || header[i - 1] != '\\')) quoted = !quoted
            if (c == ';' && !quoted) { parts.add(sb.toString()); sb.clear() } else sb.append(c)
            i++
        }
        parts.add(sb.toString())
        for (p in parts.drop(1)) {
            val eq = p.indexOf('=')
            if (eq <= 0) continue
            val key = p.substring(0, eq).trim().lowercase()
            if (key !in result) result[key] = p.substring(eq + 1).trim()
        }
        return result
    }

    private fun unquote(v: String): String {
        val t = v.trim()
        return if (t.length >= 2 && t.startsWith('"') && t.endsWith('"')) t.substring(1, t.length - 1).replace("\\\"", "\"").replace("\\\\", "\\") else t
    }

    /** RFC 5987: charset'language'percent-encoded */
    private fun decodeExtValue(raw: String): String? {
        val v = unquote(raw)
        val first = v.indexOf('\'')
        val second = if (first >= 0) v.indexOf('\'', first + 1) else -1
        if (first < 0 || second < 0) return null
        val charset = v.substring(0, first).ifBlank { "UTF-8" }
        val encoded = v.substring(second + 1)
        return try {
            URLDecoder.decode(encoded.replace("+", "%2B"), charset)
        } catch (e: Exception) {
            null
        }
    }
}
