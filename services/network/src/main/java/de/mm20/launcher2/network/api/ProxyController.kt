package de.mm20.launcher2.network.api

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable enum class ProxyKind { Socks5, HttpConnect }
data class ProxyEndpoint(val id: Int, val name: String, val kind: ProxyKind, val host: String, val port: Int, val enabled: Boolean) {
    val proxyId get() = "telos-proxy-$id"
}
@Serializable sealed interface ProxyAssignment {
    @Serializable data object SystemDefault : ProxyAssignment
    @Serializable data object Direct : ProxyAssignment
    @Serializable data class Endpoint(val id: Int) : ProxyAssignment
}

/** Credentials stay in memory and encrypted storage; they are never exposed by endpoint flows. */
interface ProxyController : EngineComponent {
    val endpoints: StateFlow<List<ProxyEndpoint>>
    val assignments: StateFlow<Map<Int, ProxyAssignment>>
    val systemDefault: StateFlow<Int?>
    val ready: StateFlow<Set<Int>>
    suspend fun add(name: String, kind: ProxyKind, host: String, port: Int, username: String, password: String): Int
    suspend fun update(id: Int, name: String, kind: ProxyKind, host: String, port: Int, username: String, password: String)
    suspend fun setEnabled(id: Int, enabled: Boolean)
    suspend fun remove(id: Int)
    suspend fun assign(appId: Int, assignment: ProxyAssignment)
    suspend fun setDefault(id: Int?)
    /** Explicit app proxy/direct assignments win; otherwise an assigned WireGuard tunnel wins over the proxy default. */
    fun routeFor(flow: FlowInfo, wireguardFallback: WgRoute): WgRoute
    fun failClosedFor(uid: Int): Boolean
    val hasPolicies: Boolean
}
