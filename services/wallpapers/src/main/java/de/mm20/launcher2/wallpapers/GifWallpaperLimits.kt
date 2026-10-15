package de.mm20.launcher2.wallpapers

/** Reject oversized native-decoder inputs before any frame allocation. */
internal fun gifWallpaperDimensions(header: ByteArray, bytes: Long): Pair<Int, Int> {
    require(bytes in 10..32L * 1024 * 1024 && header.size >= 10) { "GIF exceeds the wallpaper size limit" }
    require(String(header, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")) { "Invalid GIF header" }
    val width = (header[6].toInt() and 255) or ((header[7].toInt() and 255) shl 8)
    val height = (header[8].toInt() and 255) or ((header[9].toInt() and 255) shl 8)
    require(width in 1..4096 && height in 1..4096 && width.toLong() * height <= 4_194_304) { "GIF dimensions exceed the wallpaper limit" }
    return width to height
}
