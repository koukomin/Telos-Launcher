package de.mm20.launcher2.network.impl

import android.content.Context
import android.util.AtomicFile
import com.celzero.firestack.intra.Tunnel
import de.mm20.launcher2.network.api.*
import de.mm20.launcher2.network.impl.wg.WgKeyBox
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI

@Serializable private data class StoredProxy(val id: Int, val name: String, val kind: ProxyKind, val encryptedAddress: String, val enabled: Boolean = true)
@Serializable private data class StoredProxies(val endpoints: List<StoredProxy> = emptyList(), val assignments: Map<Int, ProxyAssignment> = emptyMap(), val defaultId: Int? = null, val nextId: Int = 1)

/** Native firestack HTTP CONNECT/SOCKS5 registry with explicit, fail-closed app routing. */
internal class DefaultProxyController(context: Context) : ProxyController {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val json = de.mm20.launcher2.serialization.Json.Lenient
    private val _endpoints = MutableStateFlow<List<ProxyEndpoint>>(emptyList())
    override val endpoints: StateFlow<List<ProxyEndpoint>> = _endpoints
    private val _assignments = MutableStateFlow<Map<Int, ProxyAssignment>>(emptyMap())
    override val assignments: StateFlow<Map<Int, ProxyAssignment>> = _assignments
    private val _default = MutableStateFlow<Int?>(null)
    override val systemDefault: StateFlow<Int?> = _default
    private val _ready = MutableStateFlow<Set<Int>>(emptySet())
    override val ready: StateFlow<Set<Int>> = _ready
    private var stored = StoredProxies()
    private var urls = emptyMap<Int, String>()
    private var tunnel: Tunnel? = null
    private val registered = HashMap<Int, String>()
    private var poll: Job? = null
    private fun file() = AtomicFile(File(app.noBackupFilesDir, "network-proxies.json"))
    @Volatile private var failedLoad = false
    init {
        scope.launch {
            try {
                val input = file()
                if (input.baseFile.exists()) {
                    check(input.baseFile.length() <= 256 * 1024)
                    stored = json.decodeFromString<StoredProxies>(input.readFully().toString(Charsets.UTF_8))
                    check(stored.endpoints.size <= 32 && stored.assignments.size <= 10_000)
                }
                publish()
            } catch (_: Exception) {
                // A corrupt existing policy must not become an implicit direct-route policy.
                stored = StoredProxies()
                failedLoad = true
            } finally { loaded.complete(Unit) }
        }
    }
    private fun publish() {
        urls = stored.endpoints.mapNotNull { item -> WgKeyBox.decrypt(item.encryptedAddress)?.let { item.id to it } }.toMap()
        _endpoints.value = stored.endpoints.map { item ->
            val uri = urls[item.id]?.let { runCatching { URI(it) }.getOrNull() }
            ProxyEndpoint(item.id, item.name, item.kind, uri?.host.orEmpty(), uri?.port ?: 0, item.enabled)
        }
        _assignments.value = stored.assignments; _default.value = stored.defaultId
    }
    private fun save(next: StoredProxies) {
        val data = json.encodeToString(StoredProxies.serializer(), next).toByteArray()
        check(data.size <= 256 * 1024)
        val atomic = file(); val output = atomic.startWrite()
        try { output.write(data); atomic.finishWrite(output) } catch (error: Throwable) { atomic.failWrite(output); throw error }
        stored = next; failedLoad = false; publish()
    }
    override suspend fun add(name: String, kind: ProxyKind, host: String, port: Int, username: String, password: String): Int = withContext(Dispatchers.IO) {
        loaded.await(); mutex.withLock {
            require(name.trim().length in 1..80 && name.none { it.isISOControl() } && stored.endpoints.size < 32)
            val url = ProxyEndpointPolicy.address(kind, host.trim(), port, username, password)
            val id = stored.nextId
            require(id in 1 until Int.MAX_VALUE)
            val next = StoredProxy(id, name.trim(), kind, WgKeyBox.encrypt(url))
            save(stored.copy(endpoints = stored.endpoints + next, nextId = id + 1)); reconcile(); id
        }
    }
    override suspend fun update(id: Int, name: String, kind: ProxyKind, host: String, port: Int, username: String, password: String) = withContext(Dispatchers.IO) {
        loaded.await(); mutex.withLock {
            require(name.trim().length in 1..80 && name.none { it.isISOControl() } && stored.endpoints.any { it.id == id })
            val address = ProxyEndpointPolicy.address(kind, host.trim(), port, username, password)
            val encrypted = WgKeyBox.encrypt(address)
            _ready.value = _ready.value - id
            save(stored.copy(endpoints = stored.endpoints.map { if (it.id == id) it.copy(name = name.trim(), kind = kind, encryptedAddress = encrypted) else it }))
            reconcile()
        }
    }
    override suspend fun setEnabled(id: Int, enabled: Boolean) = mutate { it.copy(endpoints = it.endpoints.map { p -> if (p.id == id) p.copy(enabled = enabled) else p }) }
    override suspend fun remove(id: Int) = mutate { it.copy(endpoints = it.endpoints.filterNot { p -> p.id == id }) }
    // Keep assignments to removed endpoints: affected apps remain blocked until the user chooses a route.
    override suspend fun assign(appId: Int, assignment: ProxyAssignment) = mutate {
        require(appId >= 0 && (it.assignments.size < 10_000 || appId in it.assignments))
        require(assignment !is ProxyAssignment.Endpoint || it.endpoints.any { p -> p.id == assignment.id })
        it.copy(assignments = it.assignments + (appId to assignment))
    }
    override suspend fun setDefault(id: Int?) = mutate { value ->
        require(id == null || value.endpoints.any { it.id == id }); value.copy(defaultId = id)
    }
    private suspend fun mutate(transform: (StoredProxies) -> StoredProxies) = withContext(Dispatchers.IO) {
        loaded.await(); mutex.withLock { save(transform(stored)); reconcile() }
    }
    private fun reconcile() {
        val current = tunnel ?: return
        val nextReady = HashSet<Int>()
        val ids = _endpoints.value.mapTo(HashSet()) { it.id }
        for (id in registered.keys.toList()) if (id !in ids) {
            runCatching { current.proxies.removeProxy("telos-proxy-$id") }; registered.remove(id)
        }
        for (endpoint in _endpoints.value) {
            try {
                if (!endpoint.enabled) { current.proxies.removeProxy(endpoint.proxyId); registered.remove(endpoint.id); continue }
                val address = urls[endpoint.id] ?: continue
                if (registered[endpoint.id] != address && current.proxies.hasProxy(endpoint.proxyId)) current.proxies.removeProxy(endpoint.proxyId)
                if (!current.proxies.hasProxy(endpoint.proxyId)) current.proxies.addProxy(endpoint.proxyId, address)
                if (current.proxies.hasProxy(endpoint.proxyId)) { registered[endpoint.id] = address; nextReady += endpoint.id }
            } catch (_: Exception) { /* Never register an implicit direct fallback. */ }
        }
        for (id in _ready.value - nextReady) runCatching { current.proxies.removeProxy("telos-proxy-$id") }
        _ready.value = nextReady
    }
    override suspend fun onTunnelConnected(tunnel: Tunnel) = withContext(Dispatchers.IO) {
        loaded.await(); mutex.withLock { this@DefaultProxyController.tunnel = tunnel; registered.clear(); _ready.value = emptySet(); reconcile() }
        poll?.cancel()
        poll = scope.launch { while (isActive) { delay(2_000); mutex.withLock { reconcile() } } }
    }
    override suspend fun onTunnelDisconnected() {
        poll?.cancel(); poll = null
        mutex.withLock { tunnel = null; registered.clear(); _ready.value = emptySet() }
    }
    override val hasPolicies: Boolean get() = !loaded.isCompleted || failedLoad || _default.value != null || _assignments.value.values.any { it is ProxyAssignment.Endpoint }
    override fun failClosedFor(uid: Int): Boolean {
        if (!loaded.isCompleted || failedLoad) return true
        return when (_assignments.value[if (uid < 0) -1 else uid % 100000]) {
            ProxyAssignment.Direct -> false
            is ProxyAssignment.Endpoint -> true
            else -> _default.value != null
        }
    }
    override fun routeFor(flow: FlowInfo, wireguardFallback: WgRoute): WgRoute {
        if (!loaded.isCompleted || failedLoad) return WgRoute.Block
        val assignment = _assignments.value[flow.appId] ?: ProxyAssignment.SystemDefault
        val id = when (assignment) {
            ProxyAssignment.Direct -> return WgRoute.Direct
            is ProxyAssignment.Endpoint -> assignment.id
            ProxyAssignment.SystemDefault -> {
                if (wireguardFallback != WgRoute.Direct) return wireguardFallback
                _default.value ?: return WgRoute.Direct
            }
        }
        return ProxyEndpointPolicy.route(_endpoints.value.firstOrNull { it.id == id }, id in _ready.value, flow.protocol)
    }
}
