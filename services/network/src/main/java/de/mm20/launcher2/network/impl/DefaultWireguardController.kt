package de.mm20.launcher2.network.impl

import android.content.Context
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.intra.Tunnel
import de.mm20.launcher2.network.api.ConnectionType
import de.mm20.launcher2.network.api.FlowInfo
import de.mm20.launcher2.network.api.WgAssignment
import de.mm20.launcher2.network.api.WgRoute
import de.mm20.launcher2.network.api.WgStatus
import de.mm20.launcher2.network.api.WireguardConfig
import de.mm20.launcher2.network.api.WireguardController
import de.mm20.launcher2.network.util.PersistedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
internal data class WireguardState(
    val configs: List<WireguardConfig> = emptyList(),
    val systemDefault: Int? = null,
    val assignments: Map<Int, WgAssignment> = emptyMap(),
    val nextId: Int = 1,
)

/**
 * Default WireGuard controller: persists configs and assignments, parses and exports `.conf`
 * files, and registers enabled configs as proxies `wg<id>` in the Go engine when a tunnel comes
 * up. Status is read once after registering; there is no live monitoring yet.
 */
internal class DefaultWireguardController(context: Context) : WireguardController {
    private val store = PersistedState(
        file = File(File(context.filesDir, "network"), "wireguard.json"),
        serializer = WireguardState.serializer(),
        default = { WireguardState() },
    )

    private val _configs = MutableStateFlow(store.value.configs.sortedBy { it.name.lowercase() })
    override val configs: StateFlow<List<WireguardConfig>> = _configs

    private val _status = MutableStateFlow<Map<Int, WgStatus>>(emptyMap())
    override val status: StateFlow<Map<Int, WgStatus>> = _status

    private val _systemDefault = MutableStateFlow(store.value.systemDefault)
    override val systemDefault: StateFlow<Int?> = _systemDefault

    private val _assignments = MutableStateFlow(store.value.assignments)
    override val assignments: StateFlow<Map<Int, WgAssignment>> = _assignments

    @Volatile
    private var tunnel: Tunnel? = null

    private fun publish(state: WireguardState) {
        _configs.value = state.configs.sortedBy { it.name.lowercase() }
        _systemDefault.value = state.systemDefault
        _assignments.value = state.assignments
    }

    override suspend fun importConf(text: String, name: String?): Result<WireguardConfig> {
        val parsed = try {
            WgConfParser.parse(text, name)
        } catch (e: WgConfParser.ParseException) {
            return Result.failure(e)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return add(parsed)
    }

    override fun exportConf(id: Int): String? =
        store.value.configs.firstOrNull { it.id == id }?.let(WgConfParser::export)

    override suspend fun add(config: WireguardConfig): Result<WireguardConfig> {
        var created: WireguardConfig? = null
        val state = store.update { s ->
            val c = config.copy(id = s.nextId, enabled = config.enabled)
            created = c
            s.copy(configs = s.configs + c, nextId = s.nextId + 1)
        }
        publish(state)
        created?.takeIf { it.enabled }?.let { applyToTunnel(it) }
        return Result.success(created!!)
    }

    override suspend fun update(config: WireguardConfig): Result<Unit> {
        if (store.value.configs.none { it.id == config.id }) {
            return Result.failure(NoSuchElementException("no config with id ${config.id}"))
        }
        val state = store.update { s -> s.copy(configs = s.configs.map { if (it.id == config.id) config else it }) }
        publish(state)
        removeFromTunnel(config.id)
        if (config.enabled) applyToTunnel(config)
        return Result.success(Unit)
    }

    override suspend fun remove(id: Int) {
        val state = store.update { s ->
            s.copy(
                configs = s.configs.filterNot { it.id == id },
                systemDefault = s.systemDefault.takeIf { it != id },
                assignments = s.assignments.filterValues { !(it is WgAssignment.Config && it.configId == id) },
            )
        }
        publish(state)
        removeFromTunnel(id)
    }

    override suspend fun setEnabled(id: Int, enabled: Boolean) {
        val state = store.update { s -> s.copy(configs = s.configs.map { if (it.id == id) it.copy(enabled = enabled) else it }) }
        publish(state)
        val config = state.configs.firstOrNull { it.id == id } ?: return
        if (enabled) applyToTunnel(config) else removeFromTunnel(id)
    }

    override suspend fun setSystemDefault(configId: Int?) {
        publish(store.update { it.copy(systemDefault = configId) })
    }

    override suspend fun assign(appId: Int, assignment: WgAssignment) {
        publish(store.update { s ->
            s.copy(
                assignments = if (assignment is WgAssignment.SystemDefault) s.assignments - appId else s.assignments + (appId to assignment),
            )
        })
    }

    override suspend fun generatePrivateKey(): Result<String> = try {
        Result.success(Backend.newWgPrivateKey().base64())
    } catch (e: Throwable) {
        Result.failure(e)
    }

    override fun routeFor(flow: FlowInfo): WgRoute {
        val configId = when (val a = _assignments.value[flow.appId] ?: WgAssignment.SystemDefault) {
            WgAssignment.Direct -> return WgRoute.Direct
            is WgAssignment.Config -> a.configId
            WgAssignment.SystemDefault -> _systemDefault.value ?: return WgRoute.Direct
        }
        val config = _configs.value.firstOrNull { it.id == configId } ?: return WgRoute.Direct
        if (!config.enabled) return WgRoute.Direct
        if (config.mobileOnly && flow.environment.network != ConnectionType.Mobile) return WgRoute.Direct
        val st = _status.value[configId] ?: WgStatus.Off
        val usable = st == WgStatus.Up || st == WgStatus.Connecting
        return when {
            usable -> WgRoute.Via(config.proxyId)
            config.lockdown -> WgRoute.Block
            else -> WgRoute.Direct
        }
    }

    override suspend fun onTunnelConnected(tunnel: Tunnel) {
        this.tunnel = tunnel
        _status.value = emptyMap()
        store.value.configs.filter { it.enabled }.forEach { applyToTunnel(it) }
    }

    override suspend fun onTunnelDisconnected() {
        tunnel = null
        _status.value = emptyMap()
    }

    private fun applyToTunnel(config: WireguardConfig) {
        val t = tunnel ?: return
        val result = try {
            val proxy = t.getProxies().addProxy(config.proxyId, WgConfParser.toUserspace(config))
            when (proxy.status()) {
                Backend.TOK -> WgStatus.Up
                Backend.TUP, Backend.TZZ -> WgStatus.Connecting
                Backend.TNT -> WgStatus.Down
                Backend.TPU -> WgStatus.Off
                else -> WgStatus.Error
            }
        } catch (e: Throwable) {
            WgStatus.Error
        }
        _status.value = _status.value + (config.id to result)
    }

    private fun removeFromTunnel(id: Int) {
        _status.value = _status.value - id
        val t = tunnel ?: return
        try {
            t.getProxies().removeProxy("wg$id")
        } catch (e: Throwable) {
            // already gone
        }
    }
}
