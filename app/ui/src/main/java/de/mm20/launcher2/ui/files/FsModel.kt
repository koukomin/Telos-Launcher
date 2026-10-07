package de.mm20.launcher2.ui.files

import androidx.compose.ui.graphics.Color
import de.mm20.launcher2.ui.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** What kind of file something is, decides its icon, its colour and what happens when it is opened. */
enum class FileKind(val label: String, val color: Color, val icon: Int) {
    Folder("Folder", Color(0xFFF4B400), de.mm20.launcher2.base.R.drawable.folder_24px),
    Image("Image", Color(0xFFAB47BC), de.mm20.launcher2.base.R.drawable.photo_24px),
    Video("Video", Color(0xFFEF5350), de.mm20.launcher2.base.R.drawable.videocam_24px),
    Audio("Audio", Color(0xFF26A69A), de.mm20.launcher2.base.R.drawable.music_note_24px),
    Document("Document", Color(0xFF42A5F5), de.mm20.launcher2.base.R.drawable.description_24px),
    Archive("Archive", Color(0xFFFF9800), de.mm20.launcher2.base.R.drawable.folder_zip_24px),
    Apk("App", Color(0xFF66BB6A), de.mm20.launcher2.base.R.drawable.apk_document_24px),
    Code("Code", Color(0xFF26C6DA), de.mm20.launcher2.base.R.drawable.code_24px),
    Other("File", Color(0xFF90A4AE), de.mm20.launcher2.base.R.drawable.description_24px);

    companion object {
        private val images = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "ico", "tif", "tiff", "dng", "raw", "arw", "cr2", "nef")
        private val videos = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "flv", "wmv", "mpg", "mpeg")
        private val audios = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav", "wma", "amr", "mid", "midi")
        private val documents = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "txt", "md", "csv", "epub", "log")
        private val archives = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "jar", "cab", "iso")
        private val code = setOf("kt", "java", "py", "js", "ts", "json", "xml", "html", "css", "sh", "c", "cpp", "h", "rs", "go", "yml", "yaml", "toml", "ini", "conf", "prop", "gradle", "sql")

        fun of(name: String, isDir: Boolean): FileKind {
            if (isDir) return Folder
            return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                in images -> Image
                in videos -> Video
                in audios -> Audio
                "apk", "apks", "xapk" -> Apk
                in archives -> Archive
                in code -> Code
                in documents -> Document
                else -> Other
            }
        }
    }
}

/** One file or folder in a listing. */
data class FsEntry(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    /** Like "rwxr-xr-x", empty when unknown */
    val permissions: String = "",
    val owner: String = "",
    val isLink: Boolean = false,
    val linkTarget: String? = null,
) {
    val kind: FileKind get() = FileKind.of(name, isDir)
    val extension: String get() = if (isDir) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val hidden: Boolean get() = name.startsWith(".")
}

enum class SortKey(val label: String) { Name("Name"), Modified("Date"), Size("Size"), Type("Type") }

data class SortSpec(val key: SortKey = SortKey.Name, val ascending: Boolean = true, val foldersFirst: Boolean = true)

fun List<FsEntry>.sorted(spec: SortSpec): List<FsEntry> {
    val base: Comparator<FsEntry> = when (spec.key) {
        SortKey.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortKey.Modified -> compareBy { it.modified }
        SortKey.Size -> compareBy { it.size }
        SortKey.Type -> compareBy<FsEntry> { it.extension }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
    val directed = if (spec.ascending) base else base.reversed()
    val comparator = if (spec.foldersFirst) compareBy<FsEntry> { !it.isDir }.then(directed) else directed
    return sortedWith(comparator)
}

fun formatSize(bytes: Long): String {
    if (bytes < 0) return ""
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return String.format(Locale.getDefault(), if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
}

fun formatDate(ms: Long): String =
    if (ms <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

fun parentOf(path: String): String? {
    if (path == "/" || path.isEmpty()) return null
    val trimmed = path.trimEnd('/')
    val parent = trimmed.substringBeforeLast('/', "")
    return parent.ifEmpty { "/" }
}

fun nameOf(path: String): String = path.trimEnd('/').substringAfterLast('/').ifEmpty { "/" }

fun joinPath(dir: String, name: String): String = if (dir.endsWith("/")) dir + name else "$dir/$name"
