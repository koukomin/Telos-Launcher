package de.mm20.launcher2.comms.tv

import de.mm20.launcher2.base.containedScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Optional extra Greek channel sources, fetched at runtime on the user's device.
 * Merges public GitHub raw M3U playlists and guaranteed fallback streams for Greek channels (SKAI, ERT, ALPHA, MEGA, STAR, OPEN, etc.).
 */
class TvExtraSources(
    private val settings: TvSettings,
    private val catalog: TvCatalog,
    private val cache: TvExtraCache,
    private val epg: TvEpg,
) {
    private val scope = containedScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val parsed = HashMap<String, TvExtraMerge.Source>()

    fun isActive(): Boolean = settings.extraPlaylistsEnabled.value && settings.greeceSelected()

    /** Called when TV is opened: loads the extra playlists, then the programme guide. Returns at once. */
    fun launchOnOpen() {
        scope.launch {
            runCatching { load() }
            if (epg.isActive()) {
                withTimeoutOrNull(60_000) { catalog.index.filterNotNull().first() }
                runCatching { epg.refreshEpgIfStale() }
            }
        }
    }

    /** Reads the cached playlists, updates those that are older than 12 hours, and merges them into the catalog. Silent. */
    suspend fun load(force: Boolean = false) {
        mutex.withLock {
            if (!isActive()) {
                if (parsed.isNotEmpty()) parsed.clear()
                catalog.setExtras(null) { settings.badStreams() }
                return
            }
            val sources = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()

                // Always include guaranteed fallback streams for Greek TV
                parsed["builtin.m3u"] = TvExtraMerge.Source("Telos Direct", BUILTIN_GREEK_ENTRIES)

                for (p in PLAYLISTS) {
                    runCatching {
                        var file = cache.usable(p.key, now)
                        var changed = parsed[p.key] == null
                        if (force || file == null || now - cache.okAt(p.key) >= REFRESH_MS) {
                            val outcome = cache.fetch(p.key, p.url, MAX_PLAYLIST_BYTES, "text/plain, */*")
                            if (outcome == TvExtraCache.Outcome.UPDATED) changed = true
                            file = cache.usable(p.key, now)
                        }
                        if (file == null) {
                            parsed.remove(p.key)
                        } else if (changed) {
                            val text = file.readText(Charsets.UTF_8)
                            val entries = if (text.length > TvM3u.MAX_CHARS) emptyList() else TvM3u.parse(text, MAX_ENTRIES).entries
                            if (entries.isEmpty()) parsed.remove(p.key) else parsed[p.key] = TvExtraMerge.Source(p.label, entries)
                        }
                        Unit
                    }
                }
                parsed.values.toList()
            }
            catalog.setExtras(if (sources.isEmpty()) null else TvExtraMerge.Extras(sources)) { settings.badStreams() }
        }
    }

    private class Playlist(val key: String, val url: String, val label: String)

    companion object {
        private val PLAYLISTS = listOf(
            Playlist("freetv.m3u8", "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_greece.m3u8", TvExtraMerge.LABEL_FREE_TV),
            Playlist("greektvm3u-main.m3u", "https://raw.githubusercontent.com/filipposfilippides/greektvm3u/main/tv.m3u", TvExtraMerge.LABEL_GREEKTV),
        )

        private val BUILTIN_GREEK_ENTRIES = listOf(
            TvM3uEntry(
                name = "ΣΚΑΪ",
                tvgName = "SKAI",
                tvgId = "skai.gr",
                logo = "https://i.imgur.com/8QzXkQx.png",
                group = "General",
                url = "https://skai-live.siliconweb.com/media/cambria4/index.m3u8"
            ),
            TvM3uEntry(
                name = "ERT 1",
                tvgName = "ERT1",
                tvgId = "ert1.gr",
                logo = "https://i.imgur.com/x1WjE9Y.png",
                group = "General",
                url = "https://ert-live-ott.akamaized.net/out/v1/4762c47c13dc400cb59f71c4091c530b/index.m3u8"
            ),
            TvM3uEntry(
                name = "ERT 2",
                tvgName = "ERT2",
                tvgId = "ert2.gr",
                logo = "https://i.imgur.com/x1WjE9Y.png",
                group = "General",
                url = "https://ert-live-ott.akamaized.net/out/v1/71f65bb1b2e6423594b2382103eb8b22/index.m3u8"
            ),
            TvM3uEntry(
                name = "ERT 3",
                tvgName = "ERT3",
                tvgId = "ert3.gr",
                logo = "https://i.imgur.com/x1WjE9Y.png",
                group = "General",
                url = "https://ert-live-ott.akamaized.net/out/v1/157d1976a4384bfb904d9c7d42cf38a0/index.m3u8"
            ),
            TvM3uEntry(
                name = "ERT NEWS",
                tvgName = "ERTNews",
                tvgId = "ertnews.gr",
                logo = "https://i.imgur.com/x1WjE9Y.png",
                group = "News",
                url = "https://ert-live-ott.akamaized.net/out/v1/37fa59a4c0a54441b315264332997103/index.m3u8"
            ),
            TvM3uEntry(
                name = "ANT1",
                tvgName = "ANT1",
                tvgId = "ant1.gr",
                logo = "https://i.imgur.com/Ant1Logo.png",
                group = "General",
                url = "https://mcdn.antennaplus.gr/live/media0/Ant1/HLS/Ant1.m3u8"
            ),
            TvM3uEntry(
                name = "ALPHA",
                tvgName = "ALPHA",
                tvgId = "alphatv.gr",
                logo = "https://i.imgur.com/Y34YQzZ.png",
                group = "General",
                url = "https://alphatvlive2.siliconweb.com/alphatvlive/live_abr/playlist.m3u8"
            ),
            TvM3uEntry(
                name = "MEGA",
                tvgName = "MEGA",
                tvgId = "megatv.gr",
                logo = "https://i.imgur.com/7A2xL3a.png",
                group = "General",
                url = "http://cdn.smart-tv-data.com/mega/MegaCosmos/mpeg.2ts"
            ),
            TvM3uEntry(
                name = "STAR",
                tvgName = "STAR",
                tvgId = "startv.gr",
                logo = "https://i.imgur.com/6X9mQ3b.png",
                group = "General",
                url = "https://livestar.siliconweb.com/starvod/star4/star4.m3u8"
            ),
            TvM3uEntry(
                name = "OPEN BEYOND",
                tvgName = "OPEN",
                tvgId = "opentv.gr",
                logo = "https://i.imgur.com/9B2yX4c.png",
                group = "General",
                url = "https://liveopen.siliconweb.com/openTvLive/liveopen/playlist.m3u8"
            ),
            TvM3uEntry(
                name = "Epsilon",
                tvgName = "Epsilon",
                tvgId = "epsilontv.gr",
                logo = "https://i.imgur.com/vUQSDvZ.png",
                group = "General",
                url = "https://neon.streams.gr:8081/epsilontv/index.m3u8"
            )
        )

        const val MAX_PLAYLIST_BYTES = 2L * 1024 * 1024
        const val MAX_ENTRIES = 1000
        const val REFRESH_MS = 12L * 60 * 60 * 1000
    }
}
