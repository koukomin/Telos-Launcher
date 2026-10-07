package de.mm20.launcher2.store.parser

import android.net.Uri
import de.mm20.launcher2.store.model.AppSource

/** A source understood from a web address, with a name to show until the real one is known. */
data class ParsedStoreUrl(val source: AppSource, val suggestedName: String, val packageName: String? = null)

object StoreUrlParser {

    private val giteaHosts = setOf("codeberg.org", "gitea.com")

    /**
     * Understands the addresses people paste: GitHub, GitLab (also self-hosted), Codeberg and other
     * Gitea / Forgejo servers, F-Droid, IzzyOnDroid, SourceForge, direct APK links, and any other
     * page, which is searched for APK links (the "HTML" source).
     */
    fun parse(urlStr: String): ParsedStoreUrl? {
        val raw = urlStr.trim()
        val uri = try {
            Uri.parse(raw)
        } catch (e: Exception) {
            return null
        }
        if (uri.scheme != "http" && uri.scheme != "https") return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val segments = uri.pathSegments ?: emptyList()

        when {
            host == "github.com" && segments.size >= 2 ->
                return ParsedStoreUrl(AppSource.GitHub(segments[0], segments[1].removeSuffix(".git")), "${segments[0]}/${segments[1]}")

            host == "gitlab.com" || host.startsWith("gitlab.") -> {
                // everything up to a "-" segment (or /releases) is the project path, subgroups included
                val path = segments.takeWhile { it != "-" && it != "releases" }
                if (path.size >= 2) {
                    return ParsedStoreUrl(AppSource.GitLab(host, path.joinToString("/").removeSuffix(".git")), path.last())
                }
            }

            host in giteaHosts || host.startsWith("gitea.") || host.startsWith("forgejo.") || host.startsWith("git.") && segments.size == 2 ->
                if (segments.size >= 2) {
                    return ParsedStoreUrl(AppSource.Gitea(host, segments[0], segments[1].removeSuffix(".git")), "${segments[0]}/${segments[1]}")
                }

            host == "f-droid.org" -> {
                val i = segments.indexOf("packages")
                if (i >= 0 && segments.size > i + 1) {
                    val pkg = segments[i + 1]
                    return ParsedStoreUrl(AppSource.FDroid(pkg), pkg, pkg)
                }
            }

            host == "apt.izzysoft.de" -> {
                val pkg = segments.lastOrNull()?.takeIf { it.contains('.') }
                if (pkg != null) {
                    return ParsedStoreUrl(AppSource.FDroid(pkg, IZZY_REPO), pkg, pkg)
                }
            }

            host == "sourceforge.net" -> {
                val i = segments.indexOf("projects")
                if (i >= 0 && segments.size > i + 1) {
                    return ParsedStoreUrl(AppSource.SourceForge(segments[i + 1]), segments[i + 1])
                }
            }

            raw.substringBefore('?').endsWith(".apk", ignoreCase = true) ->
                return ParsedStoreUrl(AppSource.DirectApk(raw), segments.lastOrNull()?.removeSuffix(".apk") ?: host)
        }
        // anything else: look for APK links on the page
        return ParsedStoreUrl(AppSource.Html(raw), host)
    }

    /** Kept for existing callers. */
    fun parseUrl(urlStr: String): AppSource? = parse(urlStr)?.source

    /** The address to show the user (and to export) for a source. */
    fun toUrl(source: AppSource): String = when (source) {
        is AppSource.GitHub -> "https://github.com/${source.owner}/${source.repo}"
        is AppSource.GitLab -> "https://${source.host}/${source.path}"
        is AppSource.Gitea -> "https://${source.host}/${source.owner}/${source.repo}"
        is AppSource.FDroid -> if (source.repoUrl.contains("izzysoft")) "https://apt.izzysoft.de/fdroid/index/apk/${source.packageName}"
        else "https://f-droid.org/packages/${source.packageName}"
        is AppSource.SourceForge -> "https://sourceforge.net/projects/${source.project}"
        is AppSource.Html -> source.pageUrl
        is AppSource.DirectApk -> source.downloadUrl
        is AppSource.AffiliateDirect -> source.downloadUrl
        is AppSource.AffiliatePlayStore -> "https://play.google.com/store/apps/details?id=${source.packageName}"
    }

    const val IZZY_REPO = "https://apt.izzysoft.de/fdroid/repo"
}
