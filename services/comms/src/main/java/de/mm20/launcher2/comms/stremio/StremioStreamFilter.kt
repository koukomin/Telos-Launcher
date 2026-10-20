package de.mm20.launcher2.comms.stremio

/** Optional metadata-only preferences. Missing data stays visible; empty results fall back to all sources. */
object StremioStreamFilter {
    private val resolution = Regex("(?i)\\b(480|720|1080|1440|2160)p\\b")
    private val screener = Regex("(?i)\\b(CAM|HDCAM|HDTS|TELESYNC|SCREENER|DVDSCR)\\b")
    private val size = Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(GiB|GB|MiB|MB)\\b")
    fun apply(streams: List<StreamItem>, settings: StremioPlaybackSettings, fallback: Boolean = true): List<StreamItem> {
        val filtered = streams.filter { stream ->
            val text = listOfNotNull(stream.name, stream.description, stream.filename).joinToString(" ")
            val height = resolution.find(text)?.groupValues?.get(1)?.toIntOrNull()
            val sizeMatch = size.find(text)
            val bytes = stream.sizeBytes ?: sizeMatch?.let {
                val value = it.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let null
                (value * if (it.groupValues[2].startsWith("G", true)) 1024 * 1024 * 1024 else 1024 * 1024).toLong()
            }
            (!settings.hideScreeners || !screener.containsMatchIn(text)) &&
                (settings.resolution == 0 || height == null || height == settings.resolution) &&
                (settings.maxSizeGb == 0 || bytes == null || bytes <= settings.maxSizeGb.toLong() * 1024 * 1024 * 1024)
        }
        return if (fallback && filtered.isEmpty()) streams else filtered
    }
}
