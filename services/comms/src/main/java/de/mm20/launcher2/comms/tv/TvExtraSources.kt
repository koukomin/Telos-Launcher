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
 * Optional extra Greek channel sources, fetched at runtime on the user's device only (never bundled,
 * never redistributed): the Free-TV/IPTV Greece playlist and the greektvm3u playlist, both public
 * GitHub raw files (raw.githubusercontent.com). Their streams are merged into the catalog index
 * ([TvExtraMerge]) after the iptv-org streams; channels not in iptv-org become extra channels.
 *
 * Active only when [TvSettings.extraPlaylistsEnabled] is on and Greece is among the selected
 * countries. Loaded only when TV is opened (see [TvCatalog.openHook]), never at app start. Each
 * playlist is independent and failures are silent: a source that cannot be fetched or parsed simply
 * contributes nothing (a cached copy is used for up to 7 days). Nothing here ever reports an error.
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
    suspend fun load() {
        mutex.withLock {
            if (!isActive()) {
                if (parsed.isNotEmpty()) parsed.clear()
                catalog.setExtras(null) { settings.badStreams() }
                return
            }
            val sources = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                for (p in PLAYLISTS) {
                    runCatching {
                        var file = cache.usable(p.key, now)
                        var changed = parsed[p.key] == null
                        if (file == null || now - cache.okAt(p.key) >= REFRESH_MS) {
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
                PLAYLISTS.mapNotNull { parsed[it.key] }
            }
            catalog.setExtras(if (sources.isEmpty()) null else TvExtraMerge.Extras(sources)) { settings.badStreams() }
        }
    }

    private class Playlist(val key: String, val url: String, val label: String)

    companion object {
        private val PLAYLISTS = listOf(
            Playlist("freetv.m3u8", "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_greece.m3u8", TvExtraMerge.LABEL_FREE_TV),
            Playlist("greektvm3u.m3u", "https://raw.githubusercontent.com/filipposfilippides/greektvm3u/master/tv.m3u", TvExtraMerge.LABEL_GREEKTV),
        )
        const val MAX_PLAYLIST_BYTES = 2L * 1024 * 1024
        const val MAX_ENTRIES = 1000
        const val REFRESH_MS = 12L * 60 * 60 * 1000
    }
}
