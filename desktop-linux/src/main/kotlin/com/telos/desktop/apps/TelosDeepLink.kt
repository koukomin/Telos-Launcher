package com.telos.desktop.apps

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * VOS-3 `telos://` deep links for Telos Desktop virtual apps and Kickoff search.
 *
 * Supported shapes:
 * - `telos://app/<domain-or-label>` — open a Telos virtual app window
 * - `telos://search?q=…` — open Kickoff with a query
 * - `telos://files?path=…` / `telos://open?path=…` — open Files at a path
 * - `telos://network` / `telos://settings` / `telos://store` — short aliases
 */
sealed class TelosDeepLink {
    data class OpenApp(val windowTitle: String, val domain: String? = null) : TelosDeepLink()
    data class Search(val query: String) : TelosDeepLink()
    data class OpenFiles(val path: File?) : TelosDeepLink()
    data class OpenMedia(val path: File?) : TelosDeepLink()
    data object Unknown : TelosDeepLink()
}

object TelosDeepLinkParser {
    private val appByDomain: Map<String, TelosVirtualApp> by lazy {
        TelosVirtualApps.all.associateBy { it.domain.lowercase() }
    }
    private val appByLabel: Map<String, TelosVirtualApp> by lazy {
        TelosVirtualApps.all.associateBy { it.label.lowercase() }
    }
    private val appByTitle: Map<String, TelosVirtualApp> by lazy {
        TelosVirtualApps.all.associateBy { it.windowTitle.lowercase() }
    }
    private val aliases = mapOf(
        "files" to "Files",
        "photos" to "Photos",
        "downloads" to "Downloads",
        "trash" to "Trash",
        "media" to "Media Player",
        "music" to "Media Player",
        "radio" to "Media Player",
        "video" to "Media Player",
        "notes" to "Notes",
        "calendar" to "Calendar",
        "calculator" to "Calculator",
        "clock" to "Clock",
        "network" to "Network",
        "store" to "Store",
        "settings" to "Settings",
        "system" to "System Optimizer",
        "monitor" to "System Monitor",
        "terminal" to "Quake",
        "quake" to "Quake",
    )

    fun parse(raw: String): TelosDeepLink {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return TelosDeepLink.Unknown
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return TelosDeepLink.Unknown
        val scheme = uri.scheme?.lowercase() ?: return TelosDeepLink.Unknown
        if (scheme != "telos") return TelosDeepLink.Unknown

        val host = uri.host?.lowercase()?.trim().orEmpty()
        val path = uri.path?.trim('/').orEmpty()
        val query = parseQuery(uri.rawQuery)

        return when {
            host == "search" || (host.isEmpty() && path.equals("search", true)) ->
                TelosDeepLink.Search(query["q"] ?: query["query"] ?: "")

            host == "files" || path.equals("files", true) ->
                TelosDeepLink.OpenFiles(queryPath(query))

            host == "open" || path.equals("open", true) ->
                TelosDeepLink.OpenFiles(queryPath(query))

            host == "media" || path.equals("media", true) ->
                TelosDeepLink.OpenMedia(queryPath(query))

            host == "app" || path.startsWith("app/") || path.equals("app", true) -> {
                val target = when {
                    host == "app" -> path.ifBlank { query["id"] ?: query["name"] ?: "" }
                    path.startsWith("app/") -> path.removePrefix("app/")
                    else -> query["id"] ?: ""
                }
                resolveApp(target) ?: TelosDeepLink.Unknown
            }

            host.isNotEmpty() -> resolveApp(host) ?: TelosDeepLink.Unknown

            path.isNotEmpty() -> resolveApp(path) ?: TelosDeepLink.Unknown

            else -> TelosDeepLink.Unknown
        }
    }

    fun parseArgs(args: Array<String>): TelosDeepLink? {
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a == "--open" && i + 1 < args.size -> return parse(args[i + 1])
                a.startsWith("--open=") -> return parse(a.substringAfter('='))
                a.startsWith("telos:", ignoreCase = true) -> return parse(a)
            }
            i++
        }
        return null
    }

    private fun resolveApp(raw: String): TelosDeepLink.OpenApp? {
        val key = raw.trim().lowercase().replace(' ', '_').replace('-', '_')
        if (key.isEmpty()) return null
        val domainKey = when (key) {
            "settings" -> "telos_settings"
            "monitor" -> "telos_monitor"
            "terminal", "quake" -> "telos_terminal"
            else -> {
                val base = key.removePrefix("telos_")
                val withApp = if (base.endsWith("_app")) base else "${base}_app"
                "telos_$withApp"
            }
        }
        val app = appByDomain[domainKey]
            ?: appByDomain[key]
            ?: appByLabel[raw.trim().lowercase()]
            ?: appByTitle[raw.trim().lowercase()]
            ?: aliases[key.removePrefix("telos_").removeSuffix("_app")]?.let { title ->
                appByTitle[title.lowercase()]
            }
        if (app != null) return TelosDeepLink.OpenApp(app.windowTitle, app.domain)
        aliases[key.removePrefix("telos_").removeSuffix("_app")]?.let { title ->
            return TelosDeepLink.OpenApp(title)
        }
        return null
    }

    private fun queryPath(query: Map<String, String>): File? {
        val p = query["path"] ?: query["file"] ?: query["uri"] ?: return null
        val decoded = runCatching { URLDecoder.decode(p, StandardCharsets.UTF_8) }.getOrDefault(p)
        return File(decoded)
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (part in raw.split('&')) {
            if (part.isBlank()) continue
            val k = part.substringBefore('=')
            val v = part.substringAfter('=', "")
            val dk = runCatching { URLDecoder.decode(k, StandardCharsets.UTF_8) }.getOrDefault(k)
            val dv = runCatching { URLDecoder.decode(v, StandardCharsets.UTF_8) }.getOrDefault(v)
            out[dk] = dv
        }
        return out
    }
}

/** In-process queue so CLI / handler URIs can be consumed by the Compose shell. */
object TelosDeepLinkBus {
    @Volatile
    private var pending: TelosDeepLink? = null

    fun offer(link: TelosDeepLink) {
        if (link is TelosDeepLink.Unknown) return
        pending = link
    }

    fun offerRaw(raw: String) = offer(TelosDeepLinkParser.parse(raw))

    fun take(): TelosDeepLink? {
        val cur = pending
        pending = null
        return cur
    }

    fun peek(): TelosDeepLink? = pending
}
