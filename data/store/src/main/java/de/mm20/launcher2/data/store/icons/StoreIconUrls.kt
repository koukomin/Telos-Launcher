package de.mm20.launcher2.data.store.icons

import java.net.URI

internal fun imageUrl(base: String, value: String): String? = runCatching {
    val url = URI(base).resolve(value.replace("&amp;", "&")).normalize()
    if (url.scheme != "https" || url.host.isNullOrBlank() || url.userInfo != null) return null
    val raw = url.toString()
    if (url.host == "github.com" && "/blob/" in url.path)
        "https://raw.githubusercontent.com/" + url.path.removePrefix("/").replaceFirst("/blob/", "/")
    else raw
}.getOrNull()

/** Prefer explicitly identified app artwork, excluding badges, screenshots and owner avatars. */
internal fun publishedIcons(text: String, base: String, allowOpenGraph: Boolean = false): List<String> {
    fun useful(hint: String) = Regex("icon|logo|ic_launcher", RegexOption.IGNORE_CASE).containsMatchIn(hint) &&
        !Regex("badge|shield|screenshot|avatar|banner", RegexOption.IGNORE_CASE).containsMatchIn(hint)
    val images = mutableListOf<String>()
    val attributes = Regex("([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2", setOf(RegexOption.DOT_MATCHES_ALL))
    Regex("<(?:img|meta)\\b[^>]{0,4000}>", RegexOption.IGNORE_CASE).findAll(text).forEach { tag ->
        val attrs = attributes.findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[3] }
        val src = attrs["src"] ?: attrs["content"]?.takeIf { attrs["property"] == "og:image" } ?: return@forEach
        val hint = listOf(attrs["alt"], attrs["class"], attrs["id"], attrs["rel"], src).joinToString(" ")
        if (useful(hint) || (allowOpenGraph && attrs["property"] == "og:image")) imageUrl(base, src)?.let(images::add)
    }
    Regex("!\\[([^\\]]*)]\\(([^\\s)]+)(?:[^)]*)\\)").findAll(text).forEach {
        if (useful(it.groupValues[1] + " " + it.groupValues[2])) imageUrl(base, it.groupValues[2])?.let(images::add)
    }
    return images.distinct().take(8)
}
