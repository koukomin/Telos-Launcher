package de.mm20.launcher2.comms.tv

/** One playable entry of an M3U playlist */
data class TvM3uEntry(
    val name: String, val url: String, val logo: String, val group: String,
    /** tvg-id and tvg-name attributes (empty when absent); used by the optional extra sources */
    val tvgId: String = "", val tvgName: String = "",
)

/**
 * Parser for IPTV playlists (#EXTINF with tvg-logo and group-title). Tolerant: unknown lines and
 * attributes are ignored, entries without a valid http(s) address are counted as skipped. Pure.
 */
object TvM3u {
    const val MAX_ENTRIES = 5000
    const val MAX_CHARS = 2 * 1024 * 1024

    class Parsed(val entries: List<TvM3uEntry>, val skipped: Int, val truncated: Boolean)

    private val ATTR = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    fun parse(text: String, maxEntries: Int = MAX_ENTRIES): Parsed {
        val entries = ArrayList<TvM3uEntry>()
        var skipped = 0
        var truncated = false
        var pendingName: String? = null
        var pendingLogo = ""
        var pendingGroup = ""
        var pendingId = ""
        var pendingTvgName = ""
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim().trimStart('﻿')
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val body = line.substringAfter(':', "")
                val comma = firstCommaOutsideQuotes(body)
                val attrs = if (comma >= 0) body.substring(0, comma) else body
                pendingName = (if (comma >= 0) body.substring(comma + 1) else "").trim()
                var logo = ""
                var group = ""
                var tvgName = ""
                var tvgId = ""
                for (m in ATTR.findAll(attrs)) {
                    when (m.groupValues[1].lowercase()) {
                        "tvg-logo" -> logo = m.groupValues[2]
                        "group-title" -> group = m.groupValues[2]
                        "tvg-name" -> tvgName = m.groupValues[2]
                        "tvg-id" -> tvgId = m.groupValues[2]
                    }
                }
                if (pendingName.isNullOrBlank()) pendingName = tvgName
                pendingLogo = logo
                pendingGroup = group
                pendingId = tvgId.trim().take(TvLimits.MAX_NAME)
                pendingTvgName = tvgName.trim().take(TvLimits.MAX_NAME)
                continue
            }
            if (line.startsWith("#")) continue
            // an address line
            val url = TvUrls.sanitize(line)
            val name = pendingName
            val logo = pendingLogo
            val group = pendingGroup
            val id = pendingId
            val tvgNm = pendingTvgName
            pendingName = null
            pendingLogo = ""
            pendingGroup = ""
            pendingId = ""
            pendingTvgName = ""
            if (url == null) {
                skipped++
                continue
            }
            if (entries.size >= maxEntries) {
                truncated = true
                break
            }
            val finalName = name?.trim()?.takeIf { it.isNotEmpty() } ?: hostOf(url)
            entries.add(
                TvM3uEntry(
                    name = finalName.take(TvLimits.MAX_NAME),
                    url = url,
                    logo = TvUrls.sanitize(logo).orEmpty(),
                    group = group.trim().take(TvLimits.MAX_GROUP),
                    tvgId = id,
                    tvgName = tvgNm,
                )
            )
        }
        return Parsed(entries, skipped, truncated)
    }

    private fun firstCommaOutsideQuotes(s: String): Int {
        var inQuotes = false
        for ((i, c) in s.withIndex()) {
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return i
        }
        return -1
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull().orEmpty().ifBlank { url }
}

object TvLimits {
    const val MAX_NAME = 120
    const val MAX_GROUP = 60
    /** Custom channels the user may have in total */
    const val MAX_CUSTOM = 5000
    const val MAX_FAVORITES = 2000
    const val MAX_RECENTS = 50
}
