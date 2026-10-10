package de.mm20.launcher2.comms.tv

/** One way to receive a channel. [quality] is the label of the catalog ("1080p", "480i", or empty). */
data class TvStream(
    val url: String,
    val quality: String = "",
    val userAgent: String = "",
    val referrer: String = "",
    val title: String = "",
)

/**
 * A channel of the catalog or a custom channel of the user (then [isCustom] is true and the id starts
 * with [TvIds.CUSTOM_PREFIX]). [streams] are in the order they are tried: best quality first.
 * [country] is an upper case ISO 3166 code ("GB" also for the catalog's "UK"), or empty.
 * [languages] are lower case ISO 639-3 codes ("ell"), [categories] are catalog category ids ("news").
 */
data class TvChannel(
    val id: String,
    val name: String,
    val altNames: List<String> = emptyList(),
    val country: String = "",
    val languages: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val logoUrl: String = "",
    val streams: List<TvStream> = emptyList(),
    val isCustom: Boolean = false,
    /** Only for custom channels: the group-title of the playlist or the group the user typed */
    val group: String = "",
)

data class TvCountry(val code: String, val name: String, val flag: String, val channelCount: Int)
data class TvCategory(val id: String, val name: String, val channelCount: Int)
data class TvLanguage(val code: String, val name: String, val channelCount: Int)

/** A channel the user added by hand or by importing a playlist */
data class TvCustomChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val group: String = "",
    val addedAt: Long = 0L,
) {
    fun toChannel() = TvChannel(
        id = id,
        name = name,
        logoUrl = logoUrl,
        streams = listOf(TvStream(url = streamUrl)),
        isCustom = true,
        group = group,
    )
}

data class TvRecent(val channelId: String, val playedAt: Long)

/** Result of [TvRepository.saveCustomChannel] */
enum class TvSaveResult { SAVED, INVALID_NAME, INVALID_STREAM_URL, INVALID_LOGO_URL, DUPLICATE_URL, LIMIT_REACHED }

/** Result of [TvRepository.importM3u]. [added] channels were created, [skipped] entries were invalid or duplicates. */
data class TvImportResult(
    val added: Int,
    val skipped: Int,
    /** the text was bigger than [TvM3u.MAX_CHARS]; nothing was imported */
    val tooLarge: Boolean = false,
    /** the playlist had more than [TvM3u.MAX_ENTRIES] entries, the rest was ignored */
    val truncated: Boolean = false,
)

object TvIds {
    const val CUSTOM_PREFIX = "custom:"
    fun isCustom(id: String) = id.startsWith(CUSTOM_PREFIX)
}

/** Why playback ended without a picture. The UI maps these to strings. */
enum class TvPlayerError { NO_STREAMS, ALL_STREAMS_FAILED }

/** State of the catalog download, see [TvCatalog.refreshStatus] */
data class TvRefreshStatus(
    val refreshing: Boolean = false,
    /** epoch millis of the last successful check (server answered 200 or 304), 0 = never */
    val lastCheckedAt: Long = 0L,
    /** epoch millis of the last time new data was downloaded, 0 = never */
    val lastChangedAt: Long = 0L,
    /** the last refresh failed (the old data, if any, is still used) */
    val failed: Boolean = false,
    /** technical reason of the failure, English, for details only */
    val failureDetail: String = "",
)
