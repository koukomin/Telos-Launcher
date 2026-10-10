package de.mm20.launcher2.helper

/** How Telos Files reads an archive (or a single compressed file) as a virtual folder. */
enum class ArchiveKind {
    Zip, SevenZ, Tar, TarGz, TarBz2, TarXz, TarLzma, TarZ, Cpio, Ar, Arj, Gz, Bz2, Xz, Lzma, Z;

    /** A plain compressed file (not an archive): shown as a folder holding one file */
    val isSingleFile: Boolean get() = this == Gz || this == Bz2 || this == Xz || this == Lzma || this == Z

    /** A tar stream inside a compressor */
    val isTar: Boolean get() = this == Tar || this == TarGz || this == TarBz2 || this == TarXz || this == TarLzma || this == TarZ
}

/** Pure name logic for archives: no Android classes, so it can be unit tested. */
object ArchiveFormats {
    // longest suffix first
    private val suffixes: List<Pair<String, ArchiveKind>> = listOf(
        ".tar.gz" to ArchiveKind.TarGz, ".tgz" to ArchiveKind.TarGz, ".taz" to ArchiveKind.TarGz,
        ".tar.bz2" to ArchiveKind.TarBz2, ".tbz2" to ArchiveKind.TarBz2, ".tbz" to ArchiveKind.TarBz2,
        ".tar.xz" to ArchiveKind.TarXz, ".txz" to ArchiveKind.TarXz,
        ".tar.lzma" to ArchiveKind.TarLzma, ".tlz" to ArchiveKind.TarLzma,
        ".tar.z" to ArchiveKind.TarZ,
        ".tar" to ArchiveKind.Tar,
        ".zip" to ArchiveKind.Zip, ".jar" to ArchiveKind.Zip, ".apk" to ArchiveKind.Zip, ".epub" to ArchiveKind.Zip,
        ".docx" to ArchiveKind.Zip, ".xlsx" to ArchiveKind.Zip, ".pptx" to ArchiveKind.Zip, ".odt" to ArchiveKind.Zip, ".ods" to ArchiveKind.Zip,
        ".7z" to ArchiveKind.SevenZ,
        ".cpio" to ArchiveKind.Cpio,
        ".ar" to ArchiveKind.Ar, ".a" to ArchiveKind.Ar, ".deb" to ArchiveKind.Ar,
        ".arj" to ArchiveKind.Arj,
        ".gz" to ArchiveKind.Gz, ".bz2" to ArchiveKind.Bz2, ".xz" to ArchiveKind.Xz, ".lzma" to ArchiveKind.Lzma, ".z" to ArchiveKind.Z,
    )

    /** Formats that other tools read, but Telos does not: they stay "Open with...". ACE is not possible in any free library. */
    private val unsupported = setOf("rar", "iso", "cab", "ace", "lzh", "lha", "rpm", "wim", "chm", "squashfs", "dmg", "vhd", "msi", "zst", "br")

    /** The way [name] is read, or null when Telos cannot open it as a folder */
    fun kindOf(name: String): ArchiveKind? {
        val n = name.lowercase()
        return suffixes.firstOrNull { n.endsWith(it.first) && n.length > it.first.length }?.second
    }

    fun canOpen(name: String): Boolean = kindOf(name) != null

    /** An archive format that Telos recognises but cannot open (rar, iso, ace, ...) */
    fun isKnownUnsupported(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in unsupported

    /** "photos.tar.gz" -> "photos", "my.report.zip" -> "my.report": the folder an archive is unpacked into */
    fun baseName(name: String): String {
        val n = name.lowercase()
        val suffix = suffixes.firstOrNull { n.endsWith(it.first) && n.length > it.first.length }?.first
        if (suffix != null) return name.dropLast(suffix.length)
        return name.substringBeforeLast('.', name).ifEmpty { name }
    }

    /** A name that [exists] does not report yet: "a.zip", "a (1).zip", ... */
    fun freeName(wanted: String, exists: (String) -> Boolean): String {
        if (!exists(wanted)) return wanted
        val multi = listOf(".tar.gz", ".tar.bz2", ".tar.xz").firstOrNull { wanted.lowercase().endsWith(it) && wanted.length > it.length }
        val dot = if (multi != null) wanted.length - multi.length else wanted.lastIndexOf('.')
        val base = if (dot > 0) wanted.substring(0, dot) else wanted
        val ext = if (dot > 0) wanted.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
            i++
        }
    }

    /** The file name for a new archive: [wanted] gets the extension of the chosen format if it does not have it yet */
    fun withExtension(wanted: String, extension: String): String {
        val trimmed = wanted.trim().ifEmpty { "archive" }
        return if (trimmed.lowercase().endsWith(extension.lowercase())) trimmed else trimmed + extension
    }
}
