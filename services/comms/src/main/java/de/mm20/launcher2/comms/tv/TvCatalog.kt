package de.mm20.launcher2.comms.tv

import android.content.Context
import de.mm20.launcher2.base.containedScope
import de.mm20.launcher2.comms.media.video.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection

/** What a refresh did */
enum class TvRefreshResult { UPDATED, UP_TO_DATE, FAILED }

/**
 * The channel catalog of iptv-org (https://iptv-org.github.io/api/), downloaded lazily and cached in
 * filesDir/tv. Nothing is downloaded when this object is created: only [open] (the user opened TV)
 * and [refresh] (the user pressed Refresh, or playback needs newer streams) touch the network.
 *
 * Every file is requested with a conditional GET (If-None-Match / If-Modified-Since), so an unchanged
 * catalog costs a few bytes. Addresses are https only, redirects are checked hop by hop (see Http),
 * and the system proxy settings apply because the plain HttpURLConnection is used. If a refresh
 * yields an unusable catalog (schema change), the previous index stays in use.
 */
class TvCatalog(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "tv")
    private val mutex = Mutex()
    private val scope = containedScope(Dispatchers.IO)

    private val _index = MutableStateFlow<TvIndex?>(null)
    private var baseIndex: TvIndex? = null
    private var extras: TvExtraMerge.Extras? = null
    private var extrasBad: () -> Map<String, Long> = { emptyMap() }

    /** Set by the Koin module: called (never blocking) when TV is opened, to load the optional extra sources */
    @Volatile var openHook: (() -> Unit)? = null
    private val _status = MutableStateFlow(TvRefreshStatus())

    /** The current index; null until [open] (or a refresh) has produced one */
    val index: StateFlow<TvIndex?> get() = _index

    /** Progress and outcome of the last refresh */
    val refreshStatus: StateFlow<TvRefreshStatus> get() = _status

    private class FileMeta(var etag: String = "", var lastModified: String = "", var checkedAt: Long = 0L, var changedAt: Long = 0L)

    private val meta = HashMap<String, FileMeta>()
    private var metaLoaded = false

    /**
     * Call when the user opens TV. Loads the cached catalog from disk; downloads only when there is
     * no cache yet. A cache older than 24 hours is refreshed in the background (the cached data is
     * returned immediately). Returns null when there is neither a cache nor a connection.
     */
    suspend fun open(): TvIndex? {
        runCatching { openHook?.invoke() }
        if (_index.value == null) loadFromDisk()
        if (_index.value == null) {
            refresh()
        } else {
            refreshInBackgroundIfStale()
        }
        return _index.value
    }

    /**
     * Adds (or, with null, removes) the streams and channels of the optional extra sources to [index].
     * Used by [TvExtraSources]; the merge is repeated whenever the catalog is rebuilt.
     */
    fun setExtras(value: TvExtraMerge.Extras?, bad: () -> Map<String, Long>) {
        synchronized(this) {
            extras = value
            extrasBad = bad
            val base = baseIndex ?: return
            _index.value = merged(base)
        }
    }

    private fun merged(base: TvIndex): TvIndex {
        val e = extras ?: return base
        return runCatching { TvExtraMerge.merge(base, e, extrasBad(), System.currentTimeMillis()) }.getOrDefault(base)
    }

    private fun publish(built: TvIndex) {
        synchronized(this) {
            baseIndex = built
            _index.value = merged(built)
        }
    }

    /**
     * Loads the cached catalog from disk if no index is loaded yet. Never uses the network and never
     * refreshes (for places such as launcher search that must not trigger a download). Returns the
     * index, or null when there is no usable cache.
     */
    suspend fun loadCachedOnly(): TvIndex? {
        if (_index.value == null) loadFromDisk()
        return _index.value
    }

    /** True when the catalog was never checked or the last check is older than 24 hours */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean =
        TvFailover.isStale(_status.value.lastCheckedAt, now)

    /** Starts a quiet conditional refresh when the cache is stale. Returns at once. */
    fun refreshInBackgroundIfStale() {
        if (!isStale() || _status.value.refreshing) return
        scope.launch { refresh() }
    }

    /** The Refresh button: conditional GET of all catalog files, re-indexes when something changed */
    suspend fun refresh(): TvRefreshResult = doRefresh(ALL_FILES)

    /**
     * Only the streams file (used when playback failed everywhere): true when the server has a newer
     * version, which has been indexed by the time this returns. [index] then holds the new streams.
     */
    suspend fun refreshStreams(): TvRefreshResult = doRefresh(listOf(STREAMS))

    // ---------------------------------------------------------------------------------------

    private suspend fun doRefresh(files: List<String>): TvRefreshResult = mutex.withLock {
        _status.update { it.copy(refreshing = true) }
        try {
            val outcome = withContext(Dispatchers.IO) {
                ensureMeta()
                val results = files.associateWith { fetch(it) }
                saveMeta()
                results
            }
            val updated = outcome.values.any { it is Outcome.Updated }
            val failure = outcome.entries.firstOrNull { it.value is Outcome.Failed && it.key in REQUIRED }
            var detail = (failure?.value as? Outcome.Failed)?.reason.orEmpty()
            var rebuilt = true
            if (updated || _index.value == null) {
                val built = withContext(Dispatchers.Default) { buildFromDisk() }
                val old = baseIndex
                val usable = built != null && built.size > 0 && (old == null || built.size * 5 >= old.size)
                if (usable) {
                    publish(built!!)
                } else {
                    // data that cannot be used: keep the old index
                    rebuilt = false
                    if (detail.isEmpty()) detail = "Unexpected catalog data"
                }
            }
            val s = meta[STREAMS]
            val failed = failure != null || !rebuilt
            _status.update {
                it.copy(
                    refreshing = false,
                    lastCheckedAt = s?.checkedAt ?: it.lastCheckedAt,
                    lastChangedAt = maxOf(it.lastChangedAt, s?.changedAt ?: 0L),
                    failed = failed,
                    failureDetail = if (failed) detail else "",
                )
            }
            when {
                failed -> TvRefreshResult.FAILED
                updated -> TvRefreshResult.UPDATED
                else -> TvRefreshResult.UP_TO_DATE
            }
        } catch (e: Exception) {
            _status.update { it.copy(refreshing = false, failed = true, failureDetail = e.message.orEmpty().take(200)) }
            TvRefreshResult.FAILED
        }
    }

    private suspend fun loadFromDisk() {
        mutex.withLock {
            if (_index.value != null) return
            val built = withContext(Dispatchers.Default) {
                withContext(Dispatchers.IO) { ensureMeta() }
                buildFromDisk()
            }
            if (built != null && built.size > 0) {
                publish(built)
                val s = meta[STREAMS]
                _status.update {
                    it.copy(lastCheckedAt = s?.checkedAt ?: 0L, lastChangedAt = s?.changedAt ?: 0L)
                }
            }
        }
    }

    private fun buildFromDisk(): TvIndex? {
        val channels = File(dir, CHANNELS)
        val streams = File(dir, STREAMS)
        if (!channels.isFile || !streams.isFile) return null
        fun open(name: String): () -> java.io.InputStream? = {
            File(dir, name).takeIf { it.isFile }?.let { FileInputStream(it) }
        }
        return runCatching {
            TvCatalogParser.parse(
                TvCatalogParser.Sources(
                    channels = open(CHANNELS), streams = open(STREAMS), logos = open(LOGOS),
                    feeds = open(FEEDS), blocklist = open(BLOCKLIST), categories = open(CATEGORIES),
                )
            )
        }.getOrNull()
    }

    // --- network --------------------------------------------------------------------------

    private sealed class Outcome {
        object Updated : Outcome()
        object NotModified : Outcome()
        class Failed(val reason: String) : Outcome()
    }

    private fun fetch(name: String): Outcome {
        val target = File(dir, name)
        val m = meta.getOrPut(name) { FileMeta() }
        var c: HttpURLConnection? = null
        try {
            val first = Http.open(BASE + name, USER_AGENT, "application/json")
            if (target.isFile) {
                if (m.etag.isNotEmpty()) first.setRequestProperty("If-None-Match", m.etag)
                if (m.lastModified.isNotEmpty()) first.setRequestProperty("If-Modified-Since", m.lastModified)
            }
            c = try { Http.resolve(first) } catch (e: Exception) { first.disconnect(); throw e }
            val code = c.responseCode
            val now = System.currentTimeMillis()
            if (code == 304 && target.isFile) {
                m.checkedAt = now
                return Outcome.NotModified
            }
            if (code !in 200..299) return Outcome.Failed("HTTP $code")
            dir.mkdirs()
            val tmp = File(dir, "$name.tmp")
            var total = 0L
            c.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_FILE_BYTES) throw IOException("The catalog file is too big")
                        out.write(buf, 0, n)
                    }
                }
            }
            if (total == 0L) {
                tmp.delete()
                return Outcome.Failed("Empty answer")
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw IOException("Could not store the catalog file")
            }
            m.etag = c.getHeaderField("ETag").orEmpty()
            m.lastModified = c.getHeaderField("Last-Modified").orEmpty()
            m.checkedAt = now
            m.changedAt = now
            return Outcome.Updated
        } catch (e: Exception) {
            return Outcome.Failed(e.message.orEmpty().take(200).ifEmpty { e.javaClass.simpleName })
        } finally {
            c?.disconnect()
        }
    }

    // --- meta file ------------------------------------------------------------------------

    private fun ensureMeta() {
        if (metaLoaded) return
        metaLoaded = true
        runCatching {
            val j = JSONObject(File(dir, META).readText())
            for (name in ALL_FILES) {
                val o = j.optJSONObject(name) ?: continue
                meta[name] = FileMeta(
                    o.optString("etag"), o.optString("lm"), o.optLong("checked"), o.optLong("changed"),
                )
            }
        }
    }

    private fun saveMeta() {
        runCatching {
            dir.mkdirs()
            val j = JSONObject()
            for ((name, m) in meta) {
                j.put(name, JSONObject().put("etag", m.etag).put("lm", m.lastModified).put("checked", m.checkedAt).put("changed", m.changedAt))
            }
            val tmp = File(dir, "$META.tmp")
            tmp.writeText(j.toString())
            tmp.renameTo(File(dir, META))
        }
    }

    companion object {
        const val BASE = "https://iptv-org.github.io/api/"
        private const val USER_AGENT = "Telos TV"
        private const val CHANNELS = "channels.json"
        private const val STREAMS = "streams.json"
        private const val LOGOS = "logos.json"
        private const val FEEDS = "feeds.json"
        private const val BLOCKLIST = "blocklist.json"
        private const val CATEGORIES = "categories.json"
        private const val META = "meta.json"
        private val ALL_FILES = listOf(STREAMS, CHANNELS, FEEDS, LOGOS, BLOCKLIST, CATEGORIES)
        private val REQUIRED = setOf(STREAMS, CHANNELS)
        private const val MAX_FILE_BYTES = 64L * 1024 * 1024
    }
}
