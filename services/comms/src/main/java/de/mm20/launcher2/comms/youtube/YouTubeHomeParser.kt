package de.mm20.launcher2.comms.youtube

import org.json.JSONArray
import org.json.JSONObject

/** Balanced JSON, not a regex that stops on braces inside titles. Response size is bounded upstream. */
internal fun initialYouTubeData(html: String): String? {
    val marker = html.indexOf("ytInitialData")
    if (marker < 0) return null
    val start = html.indexOf('{', marker)
    if (start < 0 || start - marker > 160) return null
    var quoted = false; var escaped = false; var depth = 0
    for (i in start until minOf(html.length, start + 4 * 1024 * 1024)) {
        val ch = html[i]
        if (quoted) {
            if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') quoted = false
        } else when (ch) {
            '"' -> quoted = true
            '{' -> depth++
            '}' -> if (--depth == 0) return html.substring(start, i + 1)
        }
    }
    return null
}

internal fun homeVideoEntries(data: String): List<YouTubeEntry> {
    val found = linkedMapOf<String, YouTubeEntry>()
    var visited = 0
    fun text(row: JSONObject?): String = row?.optString("simpleText").orEmpty().ifBlank {
        row?.optString("content").orEmpty().ifBlank {
            val runs = row?.optJSONArray("runs")
            (0 until (runs?.length() ?: 0)).joinToString("") { runs?.optJSONObject(it)?.optString("text").orEmpty() }
        }
    }
    fun accept(row: JSONObject, lockup: Boolean) {
        val id = row.optString(if (lockup) "contentId" else "videoId")
        if (!id.matches(HOME_VIDEO_ID)) return
        val metadata = if (lockup) row.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") else null
        val title = text(if (lockup) metadata?.optJSONObject("title") else row.optJSONObject("title"))
        if (title.isBlank()) return
        val owner = text(row.optJSONObject("ownerText")).ifBlank { text(row.optJSONObject("shortBylineText")) }
        val thumbnails = if (lockup) row.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")
            ?.optJSONObject("image")?.optJSONArray("sources") else row.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnail = thumbnails?.optJSONObject((thumbnails.length() - 1).coerceAtLeast(0))?.optString("url")?.takeIf { it.startsWith("https://") }
        found.putIfAbsent(id, YouTubeEntry("https://www.youtube.com/watch?v=$id", title, owner, thumbnail, -1))
    }
    fun music(row: JSONObject) {
        val endpoint = row.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
        val id = row.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: endpoint?.optString("videoId").orEmpty()
        if (!id.matches(HOME_VIDEO_ID)) return
        val columns = row.optJSONArray("flexColumns")
        fun column(index: Int) = text(columns?.optJSONObject(index)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text"))
        val title = text(row.optJSONObject("title")).ifBlank { column(0) }
        if (title.isBlank()) return
        val owner = text(row.optJSONObject("subtitle")).ifBlank { column(1) }
        val thumbs = row.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnail = thumbs?.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))?.optString("url")?.takeIf { it.startsWith("https://") }
        found.putIfAbsent(id, YouTubeEntry("https://www.youtube.com/watch?v=$id", title, owner, thumbnail, -1))
    }
    fun visit(node: Any?, depth: Int) {
        if (depth > 48 || ++visited > 30000 || found.size >= 120) return
        when (node) {
            is JSONObject -> {
                for (key in listOf("videoRenderer", "gridVideoRenderer", "compactVideoRenderer")) node.optJSONObject(key)?.let { accept(it, false) }
                node.optJSONObject("lockupViewModel")?.let { accept(it, true) }
                node.optJSONObject("musicResponsiveListItemRenderer")?.let(::music)
                node.optJSONObject("musicTwoRowItemRenderer")?.let(::music)
                val keys = node.keys()
                while (keys.hasNext()) visit(node.opt(keys.next()), depth + 1)
            }
            is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i), depth + 1)
        }
    }
    visit(JSONObject(data), 0)
    return found.values.toList()
}
private val HOME_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

private val MUSIC_CLIENT_VERSION = Regex("\"(?:INNERTUBE_CLIENT_VERSION|clientVersion)\"\\s*:\\s*\"([0-9.]+)\"")
internal fun musicClientVersionFromPage(page: String): String? = MUSIC_CLIENT_VERSION.find(page)?.groupValues?.get(1)
