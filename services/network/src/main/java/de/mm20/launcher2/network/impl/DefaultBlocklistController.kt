package de.mm20.launcher2.network.impl

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.intra.Tunnel
import de.mm20.launcher2.network.api.BlocklistBypass
import de.mm20.launcher2.network.api.BlocklistController
import de.mm20.launcher2.network.api.BlocklistGroup
import de.mm20.launcher2.network.api.BlocklistUpdateState
import de.mm20.launcher2.network.api.FlowInfo
import de.mm20.launcher2.network.api.RuleScope
import de.mm20.launcher2.network.impl.blocklist.BlocklistUpdateWorker
import de.mm20.launcher2.network.impl.blocklist.RethinkCatalog
import de.mm20.launcher2.network.impl.blocklist.RethinkDownloader
import de.mm20.launcher2.network.util.IpUtil
import de.mm20.launcher2.network.util.PersistedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
internal data class BlocklistState(
    val enabled: Set<String> = emptySet(),
    val bypass: List<BlocklistBypass> = emptyList(),
    val nextId: Long = 1,
    val lastUpdatedMs: Long = 0,
    /** Server timestamp of the downloaded files, also the name of their folder. 0 = nothing downloaded. */
    val timestamp: Long = 0,
    val autoUpdate: Boolean = true,
    val lastCheckedMs: Long = 0,
    val counts: Map<String, Long> = emptyMap(),
)

/**
 * The Rethink DNS blocklists. The catalog and the lists come from the same server and in the same
 * format as in the Rethink DNS app (see [RethinkDownloader]); the Go resolver reads the trie and rank
 * files (`setRdnsLocal`) and gets the enabled lists as a "stamp" (`flagsToStamp`).
 *
 * Files live in `filesDir/network/blocklists/<timestamp>/`. Nothing is downloaded before the user
 * asks for it. Trusted ("bypass") domains are handed to the resolver per query through
 * `DNSOpts.NOBLOCK` (see FlowBridge.onQuery).
 */
internal class DefaultBlocklistController(private val context: Context) : BlocklistController {
    private val baseDir = File(File(context.filesDir, "network"), "blocklists")
    private val store = PersistedState(
        file = File(File(context.filesDir, "network"), "blocklists.json"),
        serializer = BlocklistState.serializer(),
        default = { BlocklistState() },
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloader = RethinkDownloader()
    private val applyMutex = Mutex()
    private val updateMutex = Mutex()

    @Volatile
    private var tunnel: Tunnel? = null

    /** Timestamp of the files the Go resolver has loaded, 0 when none. */
    @Volatile
    private var loadedTs = 0L

    @Volatile
    private var nameIndex: Map<String, String> = emptyMap()

    private val _groups = MutableStateFlow<List<BlocklistGroup>>(emptyList())
    override val groups: StateFlow<List<BlocklistGroup>> = _groups

    private val _enabled = MutableStateFlow(store.value.enabled)
    override val enabled: StateFlow<Set<String>> = _enabled

    private val _installed = MutableStateFlow(false)
    override val installed: StateFlow<Boolean> = _installed

    private val _updateAvailable = MutableStateFlow(false)
    override val updateAvailable: StateFlow<Boolean> = _updateAvailable

    private val _updateState = MutableStateFlow<BlocklistUpdateState>(BlocklistUpdateState.Idle)
    override val updateState: StateFlow<BlocklistUpdateState> = _updateState

    private val _lastUpdated = MutableStateFlow(store.value.lastUpdatedMs)
    override val lastUpdatedMs: StateFlow<Long> = _lastUpdated

    private val _autoUpdate = MutableStateFlow(store.value.autoUpdate)
    override val autoUpdate: StateFlow<Boolean> = _autoUpdate

    private val _storage = MutableStateFlow(0L)
    override val storageBytes: StateFlow<Long> = _storage

    private val _blockCounts = MutableStateFlow(store.value.counts)
    override val blockCounts: StateFlow<Map<String, Long>> = _blockCounts

    private val _bypass = MutableStateFlow(store.value.bypass)
    override val bypass: StateFlow<List<BlocklistBypass>> = _bypass

    init {
        loadCatalog()
        if (_installed.value && _autoUpdate.value) scope.launch { schedule(true) }
    }

    private fun dirFor(ts: Long) = File(baseDir, ts.toString())

    /** Reads the catalog of the downloaded version, if there is one. */
    private fun loadCatalog() {
        val ts = store.value.timestamp
        val dir = dirFor(ts)
        val ok = ts > 0 && listOf(RethinkDownloader.FILETAG, RethinkDownloader.BASIC_CONFIG, RethinkDownloader.RANK, RethinkDownloader.TRIE)
            .all { File(dir, it).isFile }
        if (!ok) {
            _installed.value = false
            _groups.value = emptyList()
            nameIndex = emptyMap()
            _storage.value = 0
            return
        }
        try {
            val groups = RethinkCatalog.parse(File(dir, RethinkDownloader.FILETAG).readText())
            _groups.value = groups
            nameIndex = buildMap {
                groups.forEach { g -> g.lists.forEach { put(it.name.lowercase(), it.id) } }
            }
            _installed.value = true
            _storage.value = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            _installed.value = false
            _groups.value = emptyList()
        }
    }

    // --- selection ---

    override suspend fun setEnabled(listId: String, enabled: Boolean) {
        val state = store.update { it.copy(enabled = if (enabled) it.enabled + listId else it.enabled - listId) }
        _enabled.value = state.enabled
        applyToTunnel()
    }

    override suspend fun setGroupEnabled(groupId: String, enabled: Boolean) {
        val ids = _groups.value.firstOrNull { it.id == groupId }?.lists?.map { it.id }.orEmpty().toSet()
        val state = store.update { it.copy(enabled = if (enabled) it.enabled + ids else it.enabled - ids) }
        _enabled.value = state.enabled
        applyToTunnel()
    }

    // --- engine ---

    override suspend fun onTunnelConnected(tunnel: Tunnel) {
        this.tunnel = tunnel
        loadedTs = 0
        applyToTunnel()
        val s = store.value
        if (_installed.value && s.autoUpdate && System.currentTimeMillis() - s.lastCheckedMs > DAY_MS) {
            scope.launch { update(false) }
        }
    }

    override suspend fun onTunnelDisconnected() {
        tunnel = null
        loadedTs = 0
    }

    /** Hands the files and the selection to the running resolver. Never throws. */
    private suspend fun applyToTunnel() {
        applyMutex.withLock {
            withContext(Dispatchers.IO) {
                val t = tunnel ?: return@withContext
                try {
                    val resolver = t.resolver
                    val ts = store.value.timestamp
                    val known = _groups.value.flatMap { g -> g.lists.map { it.id } }.toSet()
                    val ids = _enabled.value.filter { it in known }
                    if (!_installed.value || ids.isEmpty()) {
                        if (loadedTs != 0L) {
                            resolver.setRdnsLocal(null, null, null, null)
                            loadedTs = 0
                        }
                        return@withContext
                    }
                    if (loadedTs != ts) {
                        val dir = dirFor(ts)
                        resolver.setRdnsLocal(
                            File(dir, RethinkDownloader.TRIE).absolutePath,
                            File(dir, RethinkDownloader.RANK).absolutePath,
                            File(dir, RethinkDownloader.BASIC_CONFIG).absolutePath,
                            File(dir, RethinkDownloader.FILETAG).absolutePath,
                        )
                        loadedTs = ts
                    }
                    val rdns = resolver.rdnsLocal
                    rdns.stamp = rdns.flagsToStamp(ids.joinToString(","), Backend.EB32)
                } catch (e: Throwable) {
                    // the resolver keeps its previous lists; load everything again next time
                    loadedTs = 0
                }
            }
        }
    }

    // --- download ---

    override suspend fun checkForUpdate(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val current = store.value.timestamp
            val check = downloader.check(current)
            store.update { it.copy(lastCheckedMs = System.currentTimeMillis()) }
            val newer = !_installed.value || (check.update && check.latest > current)
            _updateAvailable.value = newer
            Result.success(newer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun update(force: Boolean): Result<Unit> = updateMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                _updateState.value = BlocklistUpdateState.Downloading(-1f)
                val current = store.value.timestamp
                val check = runCatching { downloader.check(current) }.getOrNull()
                store.update { it.copy(lastCheckedMs = System.currentTimeMillis()) }
                val newer = check != null && check.update && check.latest > current
                if (!force && _installed.value && !newer) {
                    _updateAvailable.value = false
                    _updateState.value = BlocklistUpdateState.Idle
                    return@withContext Result.success(Unit)
                }
                val ts = check?.latest?.takeIf { it > 0 } ?: current.takeIf { it > 0 } ?: System.currentTimeMillis()
                val tmp = File(baseDir, "-$ts")
                tmp.deleteRecursively()
                try {
                    downloader.download(tmp) { _updateState.value = BlocklistUpdateState.Downloading(it) }
                    RethinkCatalog.parse(File(tmp, RethinkDownloader.FILETAG).readText())
                    val dest = dirFor(ts)
                    dest.deleteRecursively()
                    if (!tmp.renameTo(dest)) throw java.io.IOException("cannot move files")
                } finally {
                    tmp.deleteRecursively()
                }
                store.update { it.copy(timestamp = ts, lastUpdatedMs = System.currentTimeMillis()) }
                _lastUpdated.value = store.value.lastUpdatedMs
                loadCatalog()
                _updateAvailable.value = false
                applyToTunnel()
                // old versions can go now that the resolver has the new one
                baseDir.listFiles()?.filter { it.isDirectory && it.name != ts.toString() }?.forEach { it.deleteRecursively() }
                if (_autoUpdate.value) schedule(true)
                _updateState.value = BlocklistUpdateState.Idle
                Result.success(Unit)
            } catch (e: CancellationException) {
                _updateState.value = BlocklistUpdateState.Idle
                throw e
            } catch (e: Exception) {
                _updateState.value = BlocklistUpdateState.Failed(e.message ?: e.javaClass.simpleName)
                Result.failure(e)
            }
        }
    }

    override suspend fun setAutoUpdate(enabled: Boolean) {
        store.update { it.copy(autoUpdate = enabled) }
        _autoUpdate.value = enabled
        schedule(enabled && _installed.value)
    }

    private fun schedule(on: Boolean) {
        try {
            val wm = WorkManager.getInstance(context)
            if (on) {
                val request = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                    .build()
                wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } else {
                wm.cancelUniqueWork(WORK_NAME)
            }
        } catch (e: Exception) {
            // WorkManager not ready: the check on tunnel start still runs
        }
    }

    override suspend fun removeDownloaded() {
        updateMutex.withLock {
            withContext(Dispatchers.IO) {
                _installed.value = false
                applyToTunnel()
                baseDir.deleteRecursively()
                store.update { it.copy(timestamp = 0, lastUpdatedMs = 0) }
                _lastUpdated.value = 0
                loadCatalog()
                _updateAvailable.value = false
                schedule(false)
            }
        }
    }

    // --- counters ---

    override fun recordBlock(listIds: Collection<String>) {
        if (listIds.isEmpty()) return
        val index = nameIndex
        val ids = listIds.map { raw ->
            // The resolver reports "group:name"; accept plain ids and names too
            val name = raw.substringAfter(':').trim()
            index[name.lowercase()] ?: if (name.all { it.isDigit() } && name.isNotEmpty()) name else raw
        }.distinct()
        val state = store.update { s ->
            s.copy(counts = s.counts.toMutableMap().also { m -> ids.forEach { id -> m[id] = (m[id] ?: 0L) + 1 } })
        }
        _blockCounts.value = state.counts
    }

    override suspend fun resetCounts() {
        store.update { it.copy(counts = emptyMap()) }
        _blockCounts.value = emptyMap()
    }

    // --- bypass ---

    override suspend fun addBypass(scope: RuleScope, domain: String): Result<BlocklistBypass> {
        val d = IpUtil.normalizeDomain(domain)
        val body = d.removePrefix("*.")
        if (body.isEmpty() || body.contains(' ') || body.contains('/') || body.contains('*')) {
            return Result.failure(IllegalArgumentException("invalid domain"))
        }
        var result: BlocklistBypass? = null
        val state = store.update { old ->
            val existing = old.bypass.firstOrNull { it.scope == scope && it.domain == d }
            if (existing != null) {
                result = existing
                old
            } else {
                val entry = BlocklistBypass(old.nextId, scope, d)
                result = entry
                old.copy(bypass = old.bypass + entry, nextId = old.nextId + 1)
            }
        }
        _bypass.value = state.bypass
        return Result.success(result!!)
    }

    override suspend fun removeBypass(id: Long) {
        val state = store.update { it.copy(bypass = it.bypass.filterNot { b -> b.id == id }) }
        _bypass.value = state.bypass
    }

    override fun isBypassed(uid: Int, domain: String): Boolean {
        val list = _bypass.value
        if (list.isEmpty()) return false
        val d = IpUtil.normalizeDomain(domain)
        val appId = if (uid < 0) -1 else uid % FlowInfo.PER_USER_RANGE
        return list.any { b ->
            val scopeMatches = when (val s = b.scope) {
                RuleScope.System -> true
                is RuleScope.App -> s.appId == appId
            }
            scopeMatches && domainMatches(b.domain, d)
        }
    }

    companion object {
        private const val WORK_NAME = "telos_network_blocklists_update"
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /** `*.example.com` matches example.com and everything below it, other patterns match exactly. */
        fun domainMatches(pattern: String, domain: String): Boolean {
            if (pattern.startsWith("*.")) {
                val base = pattern.substring(2)
                return domain == base || domain.endsWith(".$base")
            }
            return pattern == domain
        }
    }
}
