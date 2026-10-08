package de.mm20.launcher2.downloads.logic

import de.mm20.launcher2.downloads.DownloadCategory

/** A small table instead of android.webkit.MimeTypeMap so that it works in unit tests. */
object MimeTypes {
    private val byExtension = mapOf(
        "mp4" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm", "avi" to "video/x-msvideo",
        "mov" to "video/quicktime", "3gp" to "video/3gpp", "m4v" to "video/x-m4v", "ts" to "video/mp2t", "flv" to "video/x-flv",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "ogg" to "audio/ogg", "opus" to "audio/opus",
        "flac" to "audio/flac", "wav" to "audio/x-wav", "wma" to "audio/x-ms-wma",
        "pdf" to "application/pdf", "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel", "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint", "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odt" to "application/vnd.oasis.opendocument.text", "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "txt" to "text/plain", "epub" to "application/epub+zip", "csv" to "text/csv", "rtf" to "application/rtf",
        "zip" to "application/zip", "rar" to "application/vnd.rar", "7z" to "application/x-7z-compressed",
        "tar" to "application/x-tar", "gz" to "application/gzip", "bz2" to "application/x-bzip2", "xz" to "application/x-xz",
        "apk" to "application/vnd.android.package-archive", "apks" to "application/octet-stream", "xapk" to "application/octet-stream",
        "exe" to "application/vnd.microsoft.portable-executable", "msi" to "application/x-msi", "dmg" to "application/x-apple-diskimage",
        "deb" to "application/vnd.debian.binary-package", "iso" to "application/x-iso9660-image", "img" to "application/octet-stream",
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif", "webp" to "image/webp",
        "torrent" to "application/x-bittorrent", "json" to "application/json",
    )

    private val extensionByMime: Map<String, String> = buildMap {
        for ((ext, mime) in byExtension) putIfAbsent(mime, ext)
        put("audio/mpeg", "mp3"); put("video/mp4", "mp4"); put("image/jpeg", "jpg"); put("audio/mp4", "m4a")
        put("application/octet-stream", "bin")
    }

    fun extensionOf(name: String): String? =
        name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.length <= 8 }

    fun forName(name: String): String? = extensionOf(name)?.let { byExtension[it] }

    /** Extension for a Content-Type (parameters like charset are ignored), null if unknown. application/octet-stream gives null. */
    fun extensionFor(mimeType: String?): String? {
        val m = mimeType?.substringBefore(';')?.trim()?.lowercase() ?: return null
        if (m == "application/octet-stream" || m.isEmpty()) return null
        return extensionByMime[m]
    }

    fun categoryOf(name: String, mimeType: String?): DownloadCategory {
        val ext = extensionOf(name)
        val mime = (mimeType?.substringBefore(';')?.trim()?.lowercase()).orEmpty().ifEmpty { forName(name).orEmpty() }
        return when {
            ext in setOf("apk", "apks", "xapk", "exe", "msi", "dmg", "deb", "appimage") -> DownloadCategory.Programs
            ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "iso") -> DownloadCategory.Archives
            mime.startsWith("video/") -> DownloadCategory.Video
            mime.startsWith("audio/") -> DownloadCategory.Audio
            mime == "application/pdf" || mime.startsWith("text/") || mime.contains("officedocument") ||
                mime.contains("msword") || mime.contains("ms-excel") || mime.contains("ms-powerpoint") ||
                mime.contains("opendocument") || mime == "application/epub+zip" || mime == "application/rtf" -> DownloadCategory.Documents
            mime.contains("zip") || mime.contains("tar") || mime.contains("rar") || mime.contains("7z") -> DownloadCategory.Archives
            mime == "application/vnd.android.package-archive" -> DownloadCategory.Programs
            else -> DownloadCategory.Other
        }
    }
}
