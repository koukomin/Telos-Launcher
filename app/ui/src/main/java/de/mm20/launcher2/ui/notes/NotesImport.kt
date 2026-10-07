package de.mm20.launcher2.ui.notes

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.util.zip.ZipInputStream

/**
 * Reads notes from the exports of other apps: Google Keep (Takeout, .json or the .zip), Evernote (.enex),
 * Markdown and text files (Obsidian, Notion export, Joplin export, Standard Notes ...) and zip archives
 * of those.
 */
object NotesImport {

    fun read(context: Context, uris: List<Uri>): List<Note> {
        val out = mutableListOf<Note>()
        for (uri in uris) {
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: uri.lastPathSegment.orEmpty()
            val input = context.contentResolver.openInputStream(uri) ?: continue
            input.use {
                if (name.endsWith(".zip", true)) {
                    ZipInputStream(it).use { zip ->
                        while (true) {
                            val e = zip.nextEntry ?: break
                            if (!e.isDirectory) out += readFile(e.name.substringAfterLast('/'), zip.readBytes().toString(Charsets.UTF_8))
                        }
                    }
                } else out += readFile(name, it.readBytes().toString(Charsets.UTF_8))
            }
        }
        return out
    }

    fun readFile(name: String, text: String): List<Note> = try {
        when {
            name.endsWith(".json", true) -> listOfNotNull(keep(text))
            name.endsWith(".enex", true) -> enex(text)
            name.endsWith(".md", true) || name.endsWith(".markdown", true) || name.endsWith(".txt", true) ->
                listOf(parseMarkdown(name.substringBeforeLast('.'), text))
            else -> emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Markdown with an optional front matter block (`title`, `pinned`, `tags`). */
    fun parseMarkdown(fallbackTitle: String, text: String): Note {
        var body = text.replace("\r\n", "\n")
        var title = fallbackTitle
        var pinned = false
        val labels = mutableListOf<String>()
        if (body.startsWith("---\n")) {
            val end = body.indexOf("\n---", 4)
            if (end > 0) {
                body.substring(4, end).lines().forEach { l ->
                    val k = l.substringBefore(':').trim()
                    val v = l.substringAfter(':', "").trim()
                    when (k) {
                        "title" -> if (v.isNotEmpty()) title = v.trim('"')
                        "pinned" -> pinned = v == "true"
                        "tags", "labels" -> labels += v.trim('[', ']').split(',').map { it.trim().trim('"', '#') }.filter { it.isNotEmpty() }
                    }
                }
                body = body.substring(end + 4).trimStart('\n')
            }
        }
        return Note(title = title, body = body, pinned = pinned, labels = labels)
    }

    private fun keep(text: String): Note? {
        val o = JSONObject(text)
        if (!o.has("textContent") && !o.has("listContent")) return null
        val body = StringBuilder(o.optString("textContent"))
        o.optJSONArray("listContent")?.let { a ->
            for (i in 0 until a.length()) {
                val item = a.getJSONObject(i)
                body.append(if (item.optBoolean("isChecked")) "- [x] " else "- [ ] ").append(item.optString("text")).append('\n')
            }
        }
        val labels = o.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } }.orEmpty()
        val color = when (o.optString("color")) {
            "RED" -> 1; "ORANGE" -> 2; "YELLOW" -> 3; "GREEN" -> 4; "TEAL" -> 5; "BLUE" -> 6
            "CERULEAN" -> 7; "PURPLE" -> 8; "PINK" -> 9; else -> 0
        }
        val created = o.optLong("createdTimestampUsec") / 1000
        val edited = o.optLong("userEditedTimestampUsec") / 1000
        return Note(
            title = o.optString("title"), body = body.toString().trimEnd(), color = color,
            pinned = o.optBoolean("isPinned"), archived = o.optBoolean("isArchived"), trashed = o.optBoolean("isTrashed"),
            labels = labels, createdAt = if (created > 0) created else System.currentTimeMillis(),
            modifiedAt = if (edited > 0) edited else System.currentTimeMillis(),
        )
    }

    private fun enex(text: String): List<Note> {
        val notes = mutableListOf<Note>()
        Regex("<note>(.*?)</note>", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { m ->
            val s = m.groupValues[1]
            val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(s)?.groupValues?.get(1).orEmpty()
            val content = Regex("<content>\\s*<!\\[CDATA\\[(.*?)]]>\\s*</content>", RegexOption.DOT_MATCHES_ALL).find(s)?.groupValues?.get(1).orEmpty()
            val plain = content.replace(Regex("<br\\s*/?>|</div>|</p>|</li>"), "\n").replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace(Regex("\n{3,}"), "\n\n").trim()
            val tags = Regex("<tag>(.*?)</tag>").findAll(s).map { it.groupValues[1] }.toList()
            notes += Note(title = title.trim(), body = plain, labels = tags)
        }
        return notes
    }

    /** Markdown with front matter, the file format used by the folder sync. */
    fun toMarkdown(n: Note): String = buildString {
        append("---\n")
        append("title: \"").append(n.title.replace("\"", "'")).append("\"\n")
        if (n.pinned) append("pinned: true\n")
        if (n.labels.isNotEmpty()) append("tags: [").append(n.labels.joinToString(", ")).append("]\n")
        append("---\n")
        append(n.body)
        if (!n.body.endsWith("\n")) append('\n')
    }
}
