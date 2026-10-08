package de.mm20.launcher2.network.impl

import android.content.Context
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.intra.Intra
import com.celzero.firestack.intra.Tunnel
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.network.api.DnsController
import de.mm20.launcher2.network.api.DnsHealth
import de.mm20.launcher2.network.api.DnsKind
import de.mm20.launcher2.network.api.DnsServer
import de.mm20.launcher2.network.util.IpUtil
import de.mm20.launcher2.network.util.PersistedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.io.File
import java.net.URI
import java.util.UUID

@Serializable
internal data class DnsState(
    val selectedId: String = DefaultDnsController.SYSTEM_ID,
    val custom: List<DnsServer> = emptyList(),
)

/**
 * Default DNS controller. The selected server is registered in the Go engine as transport
 * `Preferred`. Until the user picks something the System server is selected, which changes nothing
 * for the device. If the selected server cannot be registered the engine falls back to System DNS.
 */
internal class DefaultDnsController(private val context: Context) : DnsController {
    private val store = PersistedState(
        file = File(File(context.filesDir, "network"), "dns.json"),
        serializer = DnsState.serializer(),
        default = { DnsState() },
    )

    private val builtIn: List<DnsServer> = listOf(
        DnsServer(
            id = SYSTEM_ID,
            kind = DnsKind.System,
            name = context.getString(I18nR.string.net_dns_system),
            builtIn = true,
        ),
        DnsServer(
            id = "cloudflare-doh", kind = DnsKind.Doh, name = "Cloudflare",
            url = "https://cloudflare-dns.com/dns-query",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001"),
            builtIn = true,
        ),
        DnsServer(
            id = "cloudflare-dot", kind = DnsKind.Dot, name = "Cloudflare (TLS)",
            url = "tls://one.one.one.one",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001"),
            builtIn = true,
        ),
        DnsServer(
            id = "quad9-doh", kind = DnsKind.Doh, name = "Quad9",
            url = "https://dns.quad9.net/dns-query",
            bootstrapIps = listOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9"),
            builtIn = true,
        ),
        DnsServer(
            id = "quad9-dot", kind = DnsKind.Dot, name = "Quad9 (TLS)",
            url = "tls://dns.quad9.net",
            bootstrapIps = listOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9"),
            builtIn = true,
        ),
        DnsServer(
            id = "mullvad-doh", kind = DnsKind.Doh, name = "Mullvad",
            url = "https://dns.mullvad.net/dns-query",
            bootstrapIps = listOf("194.242.2.2", "2a07:e340::2"),
            builtIn = true,
        ),
        DnsServer(
            id = "adguard-doh", kind = DnsKind.Doh, name = "AdGuard",
            url = "https://dns.adguard-dns.com/dns-query",
            bootstrapIps = listOf("94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff"),
            builtIn = true,
        ),
        DnsServer(
            id = "google-doh", kind = DnsKind.Doh, name = "Google",
            url = "https://dns.google/dns-query",
            bootstrapIps = listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844"),
            builtIn = true,
        ),
    )

    private fun allServers(state: DnsState) = builtIn + state.custom

    private val _servers = MutableStateFlow(allServers(store.value))
    override val servers: StateFlow<List<DnsServer>> = _servers

    private val _selected = MutableStateFlow(resolveSelected(store.value))
    override val selected: StateFlow<DnsServer> = _selected

    private val _health = MutableStateFlow<Map<String, DnsHealth>>(emptyMap())
    override val health: StateFlow<Map<String, DnsHealth>> = _health

    @Volatile
    private var tunnel: Tunnel? = null

    /** True when the selected server is registered as `Preferred` in the running tunnel. */
    @Volatile
    private var appliedOk = false

    private fun resolveSelected(state: DnsState): DnsServer =
        allServers(state).firstOrNull { it.id == state.selectedId } ?: builtIn.first()

    private fun publish(state: DnsState) {
        _servers.value = allServers(state)
        _selected.value = resolveSelected(state)
    }

    override suspend fun select(id: String): Result<Unit> {
        val server = _servers.value.firstOrNull { it.id == id }
            ?: return Result.failure(NoSuchElementException("unknown DNS server $id"))
        val previous = store.value.selectedId
        publish(store.update { it.copy(selectedId = id) })
        val t = tunnel
        if (t != null) {
            val result = apply(t, server)
            if (result.isFailure) {
                publish(store.update { it.copy(selectedId = previous) })
                apply(t, _selected.value)
            }
            return result
        }
        return Result.success(Unit)
    }

    override suspend fun addCustom(server: DnsServer): Result<DnsServer> {
        validate(server).onFailure { return Result.failure(it) }
        if (server.kind == DnsKind.System) return Result.failure(IllegalArgumentException("System DNS exists already"))
        val entry = server.copy(id = "custom-" + UUID.randomUUID().toString().take(8), builtIn = false)
        publish(store.update { it.copy(custom = it.custom + entry) })
        return Result.success(entry)
    }

    override suspend fun updateCustom(server: DnsServer): Result<Unit> {
        validate(server).onFailure { return Result.failure(it) }
        if (store.value.custom.none { it.id == server.id }) {
            return Result.failure(NoSuchElementException("not a custom server: ${server.id}"))
        }
        publish(store.update { s -> s.copy(custom = s.custom.map { if (it.id == server.id) server.copy(builtIn = false) else it }) })
        val t = tunnel
        if (t != null && _selected.value.id == server.id) apply(t, _selected.value)
        return Result.success(Unit)
    }

    override suspend fun remove(id: String): Result<Unit> {
        if (builtIn.any { it.id == id }) return Result.failure(IllegalArgumentException("built-in servers cannot be removed"))
        val wasSelected = store.value.selectedId == id
        publish(store.update { s ->
            s.copy(
                custom = s.custom.filterNot { it.id == id },
                selectedId = if (wasSelected) SYSTEM_ID else s.selectedId,
            )
        })
        _health.value = _health.value - id
        val t = tunnel
        if (t != null && wasSelected) apply(t, _selected.value)
        return Result.success(Unit)
    }

    override fun validate(server: DnsServer): Result<Unit> {
        fun fail(what: String) = Result.failure<Unit>(IllegalArgumentException(what))
        val url = server.url.trim()
        return when (server.kind) {
            DnsKind.System -> Result.success(Unit)
            DnsKind.Plain, DnsKind.DnsProxy -> {
                val parts = url.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.isEmpty() || parts.any { IpUtil.parse(IpUtil.splitHostPort(it).first) == null }) {
                    fail("invalid address: $url")
                } else Result.success(Unit)
            }
            DnsKind.Doh -> {
                val uri = runCatching { URI(url) }.getOrNull()
                if (uri == null || (uri.scheme != "https" && uri.scheme != "http") || uri.host.isNullOrEmpty()) {
                    fail("invalid DoH URL: $url")
                } else Result.success(Unit)
            }
            DnsKind.Dot -> {
                val host = url.removePrefix("tls://").substringBefore('/').substringBefore(':')
                if (host.isEmpty() || host.contains(' ')) fail("invalid DoT host: $url") else Result.success(Unit)
            }
            DnsKind.DnsCrypt -> {
                if (!url.startsWith("sdns://") || url.length < 12) fail("invalid DNSCrypt stamp")
                else if (!server.relay.isNullOrBlank() && !server.relay.startsWith("sdns://")) fail("invalid relay stamp")
                else Result.success(Unit)
            }
            DnsKind.Odoh -> {
                val resolver = runCatching { URI(url) }.getOrNull()
                val proxy = runCatching { URI(server.relay.orEmpty()) }.getOrNull()
                if (resolver == null || proxy == null || resolver.scheme != "https" || resolver.host.isNullOrEmpty() ||
                    proxy.scheme != "https" || proxy.host.isNullOrEmpty()
                ) fail("invalid ODoH resolver or proxy URL") else Result.success(Unit)
            }
        }
    }

    override fun transportFor(uid: Int, domain: String): String {
        val kind = _selected.value.kind
        return if (kind == DnsKind.System || !appliedOk) Backend.System else Backend.Preferred
    }

    override suspend fun onTunnelConnected(tunnel: Tunnel) {
        this.tunnel = tunnel
        apply(tunnel, _selected.value)
    }

    override suspend fun onTunnelDisconnected() {
        tunnel = null
        appliedOk = false
    }

    private fun apply(tunnel: Tunnel, server: DnsServer): Result<Unit> {
        if (server.kind == DnsKind.System) {
            appliedOk = false
            return Result.success(Unit)
        }
        val ips = server.bootstrapIps.joinToString(",")
        val id = Backend.Preferred
        return try {
            when (server.kind) {
                DnsKind.System -> Unit
                DnsKind.Plain, DnsKind.DnsProxy -> {
                    val csv = server.url.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        .joinToString(",") { IpUtil.withPort(it, 53) }
                    Intra.addDNSProxy(tunnel, id, csv)
                }
                DnsKind.Doh -> Intra.addDoHTransport(tunnel, id, server.url.trim(), "", ips)
                DnsKind.Dot -> {
                    val host = server.url.trim().let { if (it.startsWith("tls://")) it else "tls://$it" }
                    Intra.addDoTTransport(tunnel, id, host, ips)
                }
                DnsKind.DnsCrypt -> {
                    server.relay?.takeIf { it.isNotBlank() }?.let { Intra.addDNSCryptRelay(tunnel, it) }
                    Intra.addDNSCryptTransport(tunnel, id, server.url.trim())
                }
                DnsKind.Odoh -> Intra.addODoHTransport(tunnel, id, server.relay.orEmpty().trim(), server.url.trim(), "")
            }
            appliedOk = true
            _health.value = _health.value + (server.id to DnsHealth.Working)
            Result.success(Unit)
        } catch (e: Throwable) {
            appliedOk = false
            _health.value = _health.value + (server.id to DnsHealth.Failing)
            Result.failure(e)
        }
    }

    companion object {
        const val SYSTEM_ID = "system"
    }
}
