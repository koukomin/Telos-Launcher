package de.mm20.launcher2.ui.media

import de.mm20.launcher2.ui.files.FileKind
import de.mm20.launcher2.ui.media.docs.DocumentTypes

/**
 * Single list of formats Telos can show a thumbnail for in Files.
 *
 * New viewer or media formats become previews automatically when they are added to
 * [DocumentTypes] or to [FileKind] image/video/audio sets. Files does not keep a second list.
 */
object PreviewCatalog {
    enum class Decoder {
        Raster, Svg, Video, Audio, Pdf, Odf, Ooxml, Epub, Comic, Psd, EmbeddedRaster, Apk,
    }

    private val odf = setOf("odt", "ods", "odp")
    private val ooxml = setOf("docx", "xlsx", "pptx")

    fun ext(name: String) = name.substringAfterLast('.', "").lowercase()

    fun decoder(nameOrExt: String): Decoder? {
        val e = ext(nameOrExt).ifBlank { nameOrExt.lowercase() }
        if (e.isEmpty()) return null
        return when {
            e == "svg" -> Decoder.Svg
            e == "psd" -> Decoder.Psd
            e == "cdr" -> Decoder.EmbeddedRaster
            e == "ai" -> Decoder.Pdf
            DocumentTypes.isComic("x.$e") -> Decoder.Comic
            e == "pdf" -> Decoder.Pdf
            e in odf -> Decoder.Odf
            e in ooxml -> Decoder.Ooxml
            e == "epub" -> Decoder.Epub
            e == "apk" || e == "apks" || e == "xapk" -> Decoder.Apk
            FileKind.of("file.$e", false) == FileKind.Image -> Decoder.Raster
            FileKind.of("file.$e", false) == FileKind.Video -> Decoder.Video
            FileKind.of("file.$e", false) == FileKind.Audio -> Decoder.Audio
            else -> null
        }
    }

    fun canPreview(nameOrExt: String) = decoder(nameOrExt) != null

    /** Extensions Files may download from a remote volume to draw a preview. */
    fun remoteExts(): Set<String> = buildSet {
        addAll(odf)
        addAll(ooxml)
        addAll(
            listOf(
                "pdf", "epub", "svg", "psd", "ai", "cdr",
                "cbz", "cbr", "cbt", "cb7",
                "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "hif", "avif", "bmp", "ico",
                "dng", "raw", "arw", "cr2", "cr3", "nef",
                "mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "hevc", "h265", "hvc",
                "mp3", "m4a", "flac", "ogg", "opus", "wav",
                "apk",
            ),
        )
        for (e in listOf("pdf", "svg", "psd", "ai", "cdr", "cbz", "cbr", "cbt", "cb7", "epub")) {
            if (canPreview(e)) add(e)
        }
    }

    fun rasterExts(): Set<String> = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "hif", "avif", "ico", "tif", "tiff", "dng",
        "raw", "arw", "cr2", "cr3", "nef", "nrw", "orf", "raf", "rw2", "pef",
    )
}
