package de.mm20.launcher2.store.model

import java.net.URI

/** Human-readable source page, never a release or an APK download. No network or AI involved. */
fun AppSource.projectPageUrl(): String = when (this) {
    is AppSource.GitHub -> "https://github.com/$owner/$repo"
    is AppSource.GitLab -> "https://$host/$path"
    is AppSource.Gitea -> "https://$host/$owner/$repo"
    is AppSource.SourceForge -> "https://sourceforge.net/projects/$project/"
    is AppSource.FDroid -> when {
        repoUrl.trimEnd('/') == "https://f-droid.org/repo" -> "https://f-droid.org/packages/$packageName/"
        runCatching { URI(repoUrl).host }.getOrNull() == "apt.izzysoft.de" -> "https://apt.izzysoft.de/fdroid/index/apk/$packageName"
        else -> sourcePage(repoUrl)
    }
    is AppSource.AffiliatePlayStore -> "https://play.google.com/store/apps/details?id=$packageName"
    is AppSource.Html -> sourcePage(pageUrl, keepPage = true)
    is AppSource.DirectApk -> sourcePage(downloadUrl)
    is AppSource.AffiliateDirect -> sourcePage(downloadUrl)
}

private fun sourcePage(url: String, keepPage: Boolean = false): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return ""
    if (uri.scheme !in setOf("https", "http") || uri.host == null) return ""
    val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
    val host = uri.host.lowercase()
    val root = "${uri.scheme}://${uri.rawAuthority.substringAfterLast('@')}"
    if (host in setOf("github.com", "raw.githubusercontent.com", "codeberg.org", "gitea.com") && segments.size >= 2) {
        val pageHost = if (host == "raw.githubusercontent.com") "github.com" else host
        return "https://$pageHost/${segments[0]}/${segments[1]}"
    }
    if (host == "sourceforge.net" && segments.size >= 2 && segments[0] == "projects")
        return "https://sourceforge.net/projects/${segments[1]}/"
    val boundary = segments.indexOfFirst { it == "-" || it == "releases" || it == "releases.atom" }
    if (boundary >= 2) return root + "/" + segments.take(boundary).joinToString("/")
    if (keepPage && !uri.path.orEmpty().endsWith(".apk", ignoreCase = true))
        return URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, null).toASCIIString()
    return "$root/"
}
