package de.mm20.launcher2.comms.tv

/**
 * The decisions of the self healing playback, kept free of Android so that they can be tested.
 * Order of events when a stream fails: try the other streams of the channel; when none is left, ask
 * the catalog once whether it was updated (new streams may have appeared); only then give up.
 */
object TvFailover {
    /** A stream that does not become ready within this time counts as failed */
    const val READY_TIMEOUT_MS = 8_000L

    /** How long a failed stream is remembered (and tried last) */
    const val BAD_TTL_MS = 24L * 60 * 60 * 1000

    /** The catalog is refreshed in the background when playback starts and it is older than this */
    const val STALE_AFTER_MS = 24L * 60 * 60 * 1000

    sealed class Decision {
        data class TryStream(val stream: TvStream) : Decision()
        /** All known streams failed: check whether the catalog has newer streams */
        object CheckCatalog : Decision()
        object GiveUp : Decision()
    }

    /** Drops entries older than [BAD_TTL_MS] */
    fun prune(bad: Map<String, Long>, now: Long): Map<String, Long> =
        bad.filter { (_, at) -> now - at in 0 until BAD_TTL_MS }

    /**
     * The order the streams are tried in: the preferred one first (when it is not known to be bad),
     * then good ones in catalog order, streams that failed recently last. Duplicated URLs are dropped.
     */
    fun order(streams: List<TvStream>, preferredUrl: String?, bad: Map<String, Long>, now: Long): List<TvStream> {
        val live = prune(bad, now)
        val unique = streams.distinctBy { it.url }
        val preferred = unique.firstOrNull { it.url == preferredUrl && it.url !in live }
        val rest = unique.filter { it !== preferred }
        val (good, failed) = rest.partition { it.url !in live }
        return listOfNotNull(preferred) + good + failed
    }

    /** What to do next, given the streams in the order of [order] and the URLs already tried in this attempt */
    fun next(ordered: List<TvStream>, tried: Set<String>, catalogChecked: Boolean): Decision {
        val untried = ordered.firstOrNull { it.url !in tried }
        return when {
            untried != null -> Decision.TryStream(untried)
            !catalogChecked -> Decision.CheckCatalog
            else -> Decision.GiveUp
        }
    }

    /** True when the cached catalog should be refreshed quietly: never checked, or older than [STALE_AFTER_MS] */
    fun isStale(lastCheckedAt: Long, now: Long): Boolean = lastCheckedAt <= 0L || now - lastCheckedAt > STALE_AFTER_MS
}
