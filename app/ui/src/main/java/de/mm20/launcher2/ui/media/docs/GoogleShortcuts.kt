package de.mm20.launcher2.ui.media.docs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import de.mm20.launcher2.ui.R
import org.json.JSONObject

/**
 * Google Drive shortcut files (.gdoc, .gsheet, ...) are tiny JSON files that only point to an online document.
 * Telos reads the pointer and hands the address to the Google app or the browser. Nothing is fetched here.
 */
object GoogleShortcuts {
    private const val MAX_BYTES = 64 * 1024
    private val allowedHosts = setOf("docs.google.com", "drive.google.com", "sites.google.com", "forms.gle")
    private val idPattern = Regex("[A-Za-z0-9_-]{10,200}")

    val extensions = setOf("gdoc", "gsheet", "gslides", "gdraw", "gform", "gsite")

    fun isShortcut(name: String) = DocumentTypes.ext(name) in extensions

    /** The extension for a mime type such as application/vnd.google-apps.document, or null */
    fun extFor(mime: String?): String? = when (mime?.lowercase()) {
        "application/vnd.google-apps.document" -> "gdoc"
        "application/vnd.google-apps.spreadsheet" -> "gsheet"
        "application/vnd.google-apps.presentation" -> "gslides"
        "application/vnd.google-apps.drawing" -> "gdraw"
        "application/vnd.google-apps.form" -> "gform"
        "application/vnd.google-apps.site" -> "gsite"
        else -> null
    }

    private fun pathFor(ext: String): String? = when (ext) {
        "gdoc" -> "document/d/%s/edit"
        "gsheet" -> "spreadsheets/d/%s/edit"
        "gslides" -> "presentation/d/%s/edit"
        "gdraw" -> "drawings/d/%s/edit"
        "gform" -> "forms/d/%s/viewform"
        else -> null
    }

    private fun allowed(url: String): Boolean = runCatching {
        val u = Uri.parse(url)
        u.scheme == "https" && u.host?.lowercase() in allowedHosts
    }.getOrDefault(false)

    /** The address the shortcut points to, or null when the content is not a usable shortcut */
    fun resolve(json: String, ext: String): Uri? = runCatching {
        val o = JSONObject(json)
        val url = o.optString("url").trim()
        if (url.isNotEmpty() && allowed(url)) return@runCatching Uri.parse(url)
        val raw = o.optString("doc_id").ifBlank { o.optString("resource_id") }.trim()
        // resource ids look like "document:1AbC..."
        val id = raw.substringAfterLast(':')
        val template = pathFor(ext) ?: return@runCatching null
        if (!idPattern.matches(id)) return@runCatching null
        Uri.parse("https://docs.google.com/" + template.format(id))
    }.getOrNull()

    /** Reads the shortcut at [uri] (at most 64 KB) and opens it. Returns whether an app was started. */
    fun open(context: Context, uri: Uri, ext: String): Boolean {
        val target = runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (out.size() <= MAX_BYTES) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                if (out.size() > MAX_BYTES) null else out.toByteArray()
            }
            bytes?.let { resolve(DocumentReaders.decodeText(it), ext) }
        }.getOrNull()
        val started = target != null && runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, target).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!started) Toast.makeText(context, context.getString(R.string.au22_files_gshortcut_failed), Toast.LENGTH_LONG).show()
        return started
    }
}
