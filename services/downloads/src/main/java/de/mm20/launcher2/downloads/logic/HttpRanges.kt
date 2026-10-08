package de.mm20.launcher2.downloads.logic

/** Range request helpers, pure for testing. */
object HttpRanges {
    /** "bytes=100-199", or "bytes=100-" for an open end */
    fun rangeHeader(start: Long, end: Long): String = if (end >= 0) "bytes=$start-$end" else "bytes=$start-"

    /** Total size from "bytes 0-0/12345"; null for "bytes 0-0/*" or garbage; 0 for "bytes */0" */
    fun totalFromContentRange(header: String?): Long? {
        val h = header?.trim() ?: return null
        if (!h.startsWith("bytes", ignoreCase = true)) return null
        val total = h.substringAfterLast('/', "").trim()
        return total.toLongOrNull()?.takeIf { it >= 0 }
    }

    /** A weak ETag can not be used for byte ranges (If-Range needs a strong validator) */
    fun ifRangeValue(etag: String?, lastModified: String?): String? = when {
        !etag.isNullOrBlank() && !etag.startsWith("W/") -> etag
        !lastModified.isNullOrBlank() -> lastModified
        else -> null
    }

    /** The file on the server is not the one we started: size or a validator differ (a missing validator never differs) */
    fun changed(
        oldTotal: Long, newTotal: Long,
        oldEtag: String?, newEtag: String?,
        oldModified: String?, newModified: String?,
    ): Boolean {
        if (oldTotal >= 0 && newTotal >= 0 && oldTotal != newTotal) return true
        if (!oldEtag.isNullOrBlank() && !newEtag.isNullOrBlank() && oldEtag != newEtag) return true
        if (!oldModified.isNullOrBlank() && !newModified.isNullOrBlank() && oldModified != newModified) return true
        return false
    }
}
