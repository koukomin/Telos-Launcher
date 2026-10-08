package de.mm20.launcher2.downloads.logic

/** Pure parts of the archive extraction (zip). Tested in MiscLogicTest. */
object ArchiveLogic {
    const val MAX_ENTRIES = 20_000
    const val MAX_TOTAL_BYTES = 20L * 1024 * 1024 * 1024

    fun isZip(name: String): Boolean = MimeTypes.extensionOf(name) == "zip"

    /** Name of the folder an archive is extracted into: the file name without ".zip" */
    fun folderName(archiveName: String): String =
        FileNames.sanitize(archiveName.substringBeforeLast('.', archiveName)).ifBlank { "archive" }

    /**
     * Where an entry of an archive is written, relative to the extraction folder, or null when the entry must not be
     * written (a directory, or no safe name). Parts like ".." and absolute paths are dropped ("zip slip").
     */
    fun targetSegments(entryName: String, isDirectory: Boolean): List<String>? {
        if (isDirectory || entryName.endsWith("/")) return null
        return TorrentPaths.safeSegments(entryName).takeIf { it.isNotEmpty() }
    }
}
