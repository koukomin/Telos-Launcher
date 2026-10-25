package de.mm20.launcher2.comms.youtube

import java.net.URI

internal object YouTubeSessionPolicy {
    fun trusted(url: String): Boolean = runCatching {
        val parsed = URI(url)
        val host = parsed.host?.lowercase(java.util.Locale.ROOT) ?: return@runCatching false
        parsed.scheme.equals("https", true) && (host == "youtube.com" || host.endsWith(".youtube.com"))
    }.getOrDefault(false)
    fun sapisid(cookie: String): String? {
        val parts = cookie.split(';').mapNotNull {
            val index = it.indexOf('=')
            if (index < 1) null else it.substring(0, index).trim() to it.substring(index + 1).trim()
        }.toMap()
        return parts["SAPISID"]?.takeIf { it.isNotBlank() } ?: parts["__Secure-3PAPISID"]?.takeIf { it.isNotBlank() }
    }
    fun mergeCookies(extractor: String?, account: String): String {
        fun entries(value: String) = value.split(';').mapNotNull { part ->
            val at = part.indexOf('=')
            if (at <= 0) null else part.substring(0, at).trim() to part.substring(at + 1).trim()
        }
        val cookies = entries(account).toMap().toMutableMap()
        entries(extractor.orEmpty()).forEach { (name, value) ->
            // Preserve the visitor/mix cookies selected by the extractor, not its session cookies.
            if (name in setOf("VISITOR_INFO1_LIVE", "YSC", "PREF")) cookies[name] = value
        }
        return cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
    }
    fun expired(status: Int, body: String = ""): Boolean = status == 401 || (status == 403 && body.contains("UNAUTHENTICATED"))
}
