package de.mm20.launcher2.comms.tv

import java.net.URI

/** Validation of addresses typed or imported by the user. Pure. */
object TvUrls {
    const val MAX_URL_LENGTH = 2048

    /**
     * The trimmed address when it is an http or https URL with a host and without user info
     * (no "user:password@"), otherwise null. Local network addresses are allowed (home IPTV boxes).
     */
    fun sanitize(raw: String?): String? {
        val url = raw?.trim().orEmpty()
        if (url.isEmpty() || url.length > MAX_URL_LENGTH) return null
        if (url.any { it.isWhitespace() || it.isISOControl() }) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.rawUserInfo != null) return null
        return url
    }
}
