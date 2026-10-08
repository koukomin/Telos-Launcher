package de.mm20.launcher2.comms.blocklist

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.mm20.launcher2.comms.media.video.torrent.TorrentSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.libtorrent4j.SessionManager
import org.libtorrent4j.swig.address
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.ip_filter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Shared block list registry for the web app ad blocker (domain lists) and the torrent peer
 * filter (IP range lists). Nothing is downloaded until the user switches a list on. Parsed lists
 * are stored in the app's files directory as compact binary files; the registry is a small JSON file.
 */
object BlockLists {
    private const val TAG = "BlockLists"
    private const val MAGIC = 0x424C4B31 // "BLK1"
    /** Cap for the decompressed text of one list */
    private const val MAX_TEXT_BYTES = 160L * 1024 * 1024
    private const val WORK_NAME = "telos_blocklists_update"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private lateinit var appContext: Context
    private val _state = MutableStateFlow(BlockListState())
    val state: StateFlow<BlockListState> = _state
    private val _updating = MutableStateFlow<Set<String>>(emptySet())
    /** Ids of the lists that are being downloaded right now */
    val updating: StateFlow<Set<String>> = _updating

    @Volatile private var initialized = false
    @Volatile private var web: DomainSet? = null

    fun init(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            appContext = context.applicationContext
            _state.value = load()
            initialized = true
        }
    }

    private fun dir() = File(appContext.filesDir, "blocklists").apply { mkdirs() }
    private fun dataFile(id: String) = File(dir(), "${id.filter { it.isLetterOrDigit() || it == '-' }}.bin")

    private fun load(): BlockListState {
        val stored = try {
            File(dir(), "registry.json").takeIf { it.exists() }
                ?.let { json.decodeFromString<BlockListState>(it.readText()) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the block list registry", e)
            null
        } ?: BlockListState()
        val presets = BlockListPresets.all.map { p ->
            val old = stored.lists.firstOrNull { it.id == p.id }
            (old ?: BlockList(p.id, p.name, p.kind, p.url, builtin = true))
                .copy(name = p.name, sourceUrl = p.url, kind = p.kind, builtin = true)
        }
        return stored.copy(lists = presets + stored.lists.filter { !it.builtin })
    }

    private fun modify(f: (BlockListState) -> BlockListState) {
        synchronized(lock) {
            val next = f(_state.value)
            _state.value = next
            try {
                val target = File(dir(), "registry.json")
                val tmp = File(dir(), "registry.json.tmp")
                tmp.writeText(json.encodeToString(BlockListState.serializer(), next))
                tmp.renameTo(target)
            } catch (e: Exception) {
                Log.w(TAG, "Could not save the block list registry", e)
            }
        }
    }

    private fun modifyList(id: String, f: (BlockList) -> BlockList) =
        modify { s -> s.copy(lists = s.lists.map { if (it.id == id) f(it) else it }) }

    // ---- user actions -------------------------------------------------------------------------

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        init(context)
        val list = _state.value.lists.firstOrNull { it.id == id } ?: return
        modifyList(id) { it.copy(enabled = enabled) }
        if (!enabled && list.sourceUrl.isNotEmpty()) {
            // downloaded data is removed again when a list is switched off
            dataFile(id).delete()
            modifyList(id) { it.copy(entryCount = 0, lastUpdate = 0, dataDate = 0, etag = null, lastModified = null, lastError = null) }
        }
        changed(list.kind)
        if (enabled && list.sourceUrl.isNotEmpty()) updateNow(context, id)
    }

    /** @return an error text if the address is not acceptable */
    fun addCustom(context: Context, kind: BlockListKind, name: String, url: String): String? {
        init(context)
        val u = url.trim()
        if (!u.startsWith("https://", ignoreCase = true) || u.length < 12 || runCatching { URL(u) }.isFailure) {
            return "invalid_url"
        }
        val id = "custom-" + UUID.randomUUID().toString().take(8)
        val list = BlockList(id, name.trim().ifEmpty { URL(u).host }, kind, u, enabled = true)
        modify { it.copy(lists = it.lists + list) }
        changed(kind)
        updateNow(context, id)
        return null
    }

    /** Reads a local file once; imported lists are never updated automatically. */
    suspend fun importFile(context: Context, kind: BlockListKind, name: String, uri: Uri): String? {
        init(context)
        val id = "file-" + UUID.randomUUID().toString().take(8)
        return withContext(Dispatchers.IO) {
            try {
                val stored = context.contentResolver.openInputStream(uri)!!.use { storeStream(id, kind, it) }
                val count = stored.count
                if (count == 0) {
                    dataFile(id).delete()
                    return@withContext "empty"
                }
                val list = BlockList(id, name.trim().ifEmpty { "Imported list" }, kind, "", enabled = true,
                    lastUpdate = System.currentTimeMillis(), dataDate = stored.dataDate, entryCount = count)
                modify { it.copy(lists = it.lists + list) }
                changed(kind)
                null
            } catch (e: Exception) {
                dataFile(id).delete()
                e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun remove(context: Context, id: String) {
        init(context)
        val list = _state.value.lists.firstOrNull { it.id == id } ?: return
        if (list.builtin) return
        dataFile(id).delete()
        modify { it.copy(lists = it.lists.filter { l -> l.id != id }) }
        changed(list.kind)
    }

    fun setSchedule(context: Context, schedule: UpdateSchedule) {
        init(context)
        modify { it.copy(schedule = schedule) }
        reschedule()
    }

    fun setWifiOnly(context: Context, wifiOnly: Boolean) {
        init(context)
        modify { it.copy(wifiOnly = wifiOnly) }
        reschedule()
    }

    /** Downloads one list (or all enabled lists with a source address) now, regardless of Wi-Fi. */
    fun updateNow(context: Context, id: String? = null) {
        init(context)
        scope.launch {
            val targets = _state.value.lists.filter {
                it.enabled && it.sourceUrl.isNotEmpty() && (id == null || it.id == id)
            }
            for (l in targets) updateList(l.id)
        }
    }

    // ---- download -----------------------------------------------------------------------------

    /** Used by the periodic worker. @return true if no list failed */
    internal suspend fun updateAllEnabled(context: Context): Boolean {
        init(context)
        var ok = true
        for (l in _state.value.lists.filter { it.enabled && it.sourceUrl.isNotEmpty() }) {
            if (!updateList(l.id)) ok = false
        }
        return ok
    }

    private suspend fun updateList(id: String): Boolean = withContext(Dispatchers.IO) {
        val list = _state.value.lists.firstOrNull { it.id == id } ?: return@withContext true
        if (id in _updating.value) return@withContext true
        _updating.value = _updating.value + id
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(list.sourceUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "TelosLauncher-BlockLists/1.0")
                if (dataFile(id).exists()) {
                    list.etag?.let { setRequestProperty("If-None-Match", it) }
                    list.lastModified?.let { setRequestProperty("If-Modified-Since", it) }
                }
            }
            val code = conn.responseCode
            val now = System.currentTimeMillis()
            if (code == 304) {
                modifyList(id) { it.copy(lastUpdate = now, lastError = null) }
                return@withContext true
            }
            if (code != 200) throw java.io.IOException("HTTP $code")
            val stored = conn.inputStream.use { storeStream(id, list.kind, it) }
            if (stored.count == 0) throw java.io.IOException("No entries found")
            val etag = conn.getHeaderField("ETag")
            val modified = conn.getHeaderField("Last-Modified")
            // the date written in the list wins over the server's Last-Modified (which for files in a repository is the fetch time)
            val dataDate = stored.dataDate.takeIf { it > 0 } ?: conn.lastModified.takeIf { it > 0 } ?: 0L
            modifyList(id) {
                it.copy(lastUpdate = now, dataDate = dataDate, etag = etag, lastModified = modified, entryCount = stored.count, lastError = null)
            }
            changed(list.kind)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Update of ${list.name} failed", e)
            // the previous version of the list stays in place
            modifyList(id) { it.copy(lastError = e.message ?: e.javaClass.simpleName) }
            false
        } finally {
            conn?.disconnect()
            _updating.value = _updating.value - id
        }
    }

    private class Stored(val count: Int, val dataDate: Long)

    /** Parses [input] and replaces the stored list atomically. */
    private fun storeStream(id: String, kind: BlockListKind, input: java.io.InputStream): Stored {
        var gzDate = 0L
        val reader = BlockListParser.openText(input, MAX_TEXT_BYTES) { gzDate = it }
        var date = 0L
        val data: LongArray = when (kind) {
            BlockListKind.WEB -> DomainSet.of(BlockListParser.parseDomains(reader)).toArray()
            BlockListKind.TORRENT_IP -> {
                val r = BlockListParser.parseIpRangesInfo(reader, BlockListPresets.byId(id)?.skipReserved == true)
                date = r.dataDate.takeIf { it > 0 } ?: gzDate
                r.ranges
            }
        }
        val count = if (kind == BlockListKind.WEB) data.size else data.size / 2
        if (count == 0) return Stored(0, 0)
        val target = dataFile(id)
        val tmp = File(target.parentFile, target.name + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(data.size)
            for (v in data) out.writeLong(v)
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw java.io.IOException("Could not store the list")
        }
        return Stored(count, date)
    }

    private fun readData(id: String): LongArray? = try {
        val f = dataFile(id)
        if (!f.exists()) null else DataInputStream(f.inputStream().buffered()).use { input ->
            if (input.readInt() != MAGIC) return null
            val n = input.readInt()
            LongArray(n) { input.readLong() }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read list $id", e)
        null
    }

    // ---- consumers ----------------------------------------------------------------------------

    private fun changed(kind: BlockListKind) {
        reschedule()
        scope.launch {
            if (kind == BlockListKind.WEB) reloadWeb() else TorrentSession.reapplyBlockList()
        }
    }

    /** Loads the enabled web lists into memory (no-op if it already happened). */
    fun ensureWebLoaded(context: Context) {
        init(context)
        if (web == null) scope.launch { if (web == null) reloadWeb() }
    }

    fun reloadWeb() {
        val sets = _state.value.lists
            .filter { it.enabled && it.kind == BlockListKind.WEB }
            .mapNotNull { readData(it.id) }
            .map { DomainSet.fromHashes(it) }
        web = DomainSet.merge(sets)
    }

    /** Fast, allocation-light check against all enabled web lists. */
    fun isWebBlocked(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        return web?.matches(host) == true
    }

    /**
     * Replaces the IP filter of a running torrent session with the enabled torrent lists.
     * Safe to call from any thread; failures are logged and leave the session untouched.
     */
    fun applyToSession(sm: SessionManager) {
        try {
            val session = sm.swig() ?: return
            val ranges = _state.value.lists
                .filter { it.enabled && it.kind == BlockListKind.TORRENT_IP }
                .mapNotNull { readData(it.id) }
            val filter = ip_filter()
            if (ranges.isNotEmpty()) {
                val total = ranges.sumOf { it.size / 2 }
                val starts = LongArray(total)
                val ends = LongArray(total)
                var n = 0
                for (r in ranges) for (i in r.indices step 2) { starts[n] = r[i]; ends[n] = r[i + 1]; n++ }
                val merged = BlockListParser.mergeRanges(starts, ends, n)
                val blocked = ip_filter.access_flags.blocked.swigValue().toLong()
                val ec = error_code()
                for (i in merged.indices step 2) {
                    val a = address.from_string(BlockListParser.formatIpv4(merged[i]), ec)
                    val b = address.from_string(BlockListParser.formatIpv4(merged[i + 1]), ec)
                    filter.add_rule(a, b, blocked)
                }
            }
            session.set_ip_filter(filter)
        } catch (e: Throwable) {
            Log.w(TAG, "Could not apply the peer block list", e)
        }
    }

    // ---- schedule -----------------------------------------------------------------------------

    private fun reschedule() {
        try {
            val s = _state.value
            val wm = WorkManager.getInstance(appContext)
            val anyList = s.lists.any { it.enabled && it.sourceUrl.isNotEmpty() }
            if (s.schedule == UpdateSchedule.OFF || !anyList) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val days = if (s.schedule == UpdateSchedule.DAILY) 1L else 7L
            val request = PeriodicWorkRequestBuilder<BlockListWorker>(days, TimeUnit.DAYS)
                .setInitialDelay(days, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (s.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule the block list update", e)
        }
    }
}

class BlockListWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        BlockLists.updateAllEnabled(applicationContext)
        return Result.success()
    }
}
