/*
 * The way configs are registered as proxies "wg<id>" in the engine, the status mapping and the
 * ping to get a handshake follow Rethink DNS (GoVpnAdapter.addWgProxy, WireguardManager).
 *
 * Copyright 2023 RethinkDNS and its authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Adapted for Telos Network.
 */
package de.mm20.launcher2.network.impl

import android.content.Context
import android.util.Base64
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.intra.Tunnel
import de.mm20.launcher2.network.api.ConnectionType
import de.mm20.launcher2.network.api.FlowInfo
import de.mm20.launcher2.network.api.WgAssignment
import de.mm20.launcher2.network.api.WgConfigError
import de.mm20.launcher2.network.api.WgConfigException
import de.mm20.launcher2.network.api.WgRoute
import de.mm20.launcher2.network.api.WgStats
import de.mm20.launcher2.network.api.WgStatus
import de.mm20.launcher2.network.api.WireguardConfig
import de.mm20.launcher2.network.api.WireguardController
import de.mm20.launcher2.network.impl.wg.WgCurve25519
import de.mm20.launcher2.network.impl.wg.WgKeyBox
import de.mm20.launcher2.network.impl.wg.WgValidator
import de.mm20.launcher2.network.util.PersistedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** The complete WireGuard state with plain-text keys. Lives in memory only. */
internal data class WireguardState(
    val configs: List<WireguardConfig> = emptyList(),
    val systemDefault: Int? = null,
    val assignments: Map<Int, WgAssignment> = emptyMap(),
    val nextId: Int = 1,
)

/** The file format. Private and pre-shared keys are encrypted with [WgKeyBox]. */
@Serializable
internal data class StoredWireguardState(
    val configs: List<WireguardConfig> = emptyList(),
    val systemDefault: Int? = null,
    val assignments: Map<Int, WgAssignment> = emptyMap(),
    val nextId: Int = 1,
)

/**
 * Default WireGuard controller. Persists configs (keys encrypted with the Android Keystore) and
 * assignments, parses and exports `.conf` files, registers enabled configs as proxies `wg<id>` in
 * the Go engine whenever a tunnel is up, polls their status and traffic, and decides per flow
 * which proxy it takes ([routeFor]).
 */
internal class DefaultWireguardController(context: Context) : WireguardController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val engineLock = Mutex()

    private val store = PersistedState(
        file = File(File(context.filesDir, "network"), "wireguard.json"),
        serializer = StoredWireguardState.serializer(),
        default = { StoredWireguardState() },
    )

    /** Plain text to ciphertext, so that unchanged keys are not encrypted again on every save. */
    private val encCache = ConcurrentHashMap<String, String>()

    private val _configs = MutableStateFlow<List<WireguardConfig>>(emptyList())
    override val configs: StateFlow<List<WireguardConfig>> = _configs

    private val _status = MutableStateFlow<Map<Int, WgStatus>>(emptyMap())
    override val status: StateFlow<Map<Int, WgStatus>> = _status

    private val _stats = MutableStateFlow<Map<Int, WgStats>>(emptyMap())
    override val stats: StateFlow<Map<Int, WgStats>> = _stats

    private val _systemDefault = MutableStateFlow<Int?>(null)
    override val systemDefault: StateFlow<Int?> = _systemDefault

    private val _assignments = MutableStateFlow<Map<Int, WgAssignment>>(emptyMap())
    override val assignments: StateFlow<Map<Int, WgAssignment>> = _assignments

    @Volatile
    private var state = WireguardState()

    @Volatile
    private var tunnel: Tunnel? = null
    private var pollJob: Job? = null

    init {
        state = load(store.value)
        publish(state)
    }

    // ---- persistence ----

    private fun load(stored: StoredWireguardState): WireguardState {
        val configs = stored.configs.map { c ->
            c.copy(
                wgInterface = c.wgInterface.copy(privateKey = decrypt(c.wgInterface.privateKey)),
                peers = c.peers.map { p -> p.copy(presharedKey = p.presharedKey?.let(::decrypt)) },
            )
        }
        val ids = configs.map { it.id }.toSet()
        return WireguardState(
            configs = configs,
            systemDefault = stored.systemDefault?.takeIf { it in ids },
            assignments = stored.assignments.filterValues { it !is WgAssignment.Config || it.configId in ids },
            nextId = maxOf(stored.nextId, (ids.maxOrNull() ?: 0) + 1),
        )
    }

    private fun decrypt(value: String): String {
        val plain = WgKeyBox.decrypt(value) ?: return ""
        if (value.startsWith(WgKeyBox.PREFIX)) encCache[plain] = value
        return plain
    }

    private fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return encCache.getOrPut(plain) { WgKeyBox.encrypt(plain) }
    }

    private fun toStored(s: WireguardState) = StoredWireguardState(
        configs = s.configs.map { c ->
            c.copy(
                wgInterface = c.wgInterface.copy(privateKey = encrypt(c.wgInterface.privateKey)),
                peers = c.peers.map { p -> p.copy(presharedKey = p.presharedKey?.let(::encrypt)) },
            )
        },
        systemDefault = s.systemDefault,
        assignments = s.assignments,
        nextId = s.nextId,
    )

    private fun publish(s: WireguardState) {
        _configs.value = s.configs.sortedBy { it.name.lowercase() }
        _systemDefault.value = s.systemDefault
        _assignments.value = s.assignments
    }

    /** Applies [block] to the state, saves it and publishes it. On failure nothing changes. */
    private suspend fun mutate(block: (WireguardState) -> WireguardState): Result<WireguardState> =
        mutex.withLock {
            try {
                val new = block(state)
                val stored = toStored(new)
                store.update { stored }
                state = new
                publish(new)
                Result.success(new)
            } catch (e: WgConfigException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(WgConfigException(WgConfigError.Storage, e.message.orEmpty()))
            }
        }

    // ---- API ----

    override suspend fun importConf(text: String, name: String?): Result<WireguardConfig> {
        val parsed = try {
            WgConfParser.parse(text, name)
        } catch (e: WgConfigException) {
            return Result.failure(e)
        } catch (e: Exception) {
            return Result.failure(WgConfigException(WgConfigError.Syntax, e.message.orEmpty()))
        }
        return add(parsed)
    }

    override fun exportConf(id: Int): String? =
        state.configs.firstOrNull { it.id == id }?.let(WgConfParser::export)

    override suspend fun add(config: WireguardConfig): Result<WireguardConfig> {
        var created: WireguardConfig? = null
        val result = mutate { s ->
            val c = WgValidator.normalize(config).copy(id = s.nextId)
            created = c
            s.copy(configs = s.configs + c, nextId = s.nextId + 1)
        }
        result.exceptionOrNull()?.let { return Result.failure(it) }
        val c = created ?: return Result.failure(WgConfigException(WgConfigError.Other))
        if (c.enabled) applyToTunnel(c)
        return Result.success(c)
    }

    override suspend fun update(config: WireguardConfig): Result<Unit> {
        var updated: WireguardConfig? = null
        val result = mutate { s ->
            if (s.configs.none { it.id == config.id }) throw WgConfigException(WgConfigError.NotFound)
            val c = WgValidator.normalize(config)
            updated = c
            s.copy(configs = s.configs.map { if (it.id == c.id) c else it })
        }
        result.exceptionOrNull()?.let { return Result.failure(it) }
        val c = updated!!
        removeFromTunnel(c.id)
        if (c.enabled) applyToTunnel(c)
        return Result.success(Unit)
    }

    override suspend fun remove(id: Int) {
        mutate { s ->
            s.copy(
                configs = s.configs.filterNot { it.id == id },
                systemDefault = s.systemDefault.takeIf { it != id },
                assignments = s.assignments.filterValues { !(it is WgAssignment.Config && it.configId == id) },
            )
        }
        removeFromTunnel(id)
    }

    override suspend fun setEnabled(id: Int, enabled: Boolean) {
        val new = mutate { s ->
            s.copy(configs = s.configs.map { if (it.id == id) it.copy(enabled = enabled) else it })
        }.getOrNull() ?: return
        val config = new.configs.firstOrNull { it.id == id } ?: return
        if (enabled) {
            removeFromTunnel(id)
            applyToTunnel(config)
        } else {
            removeFromTunnel(id)
        }
    }

    override suspend fun setSystemDefault(configId: Int?) {
        mutate { s -> s.copy(systemDefault = configId?.takeIf { id -> s.configs.any { it.id == id } }) }
    }

    override suspend fun assign(appId: Int, assignment: WgAssignment) = assignAll(listOf(appId), assignment)

    override suspend fun assignAll(appIds: Collection<Int>, assignment: WgAssignment) {
        mutate { s ->
            if (assignment is WgAssignment.Config && s.configs.none { it.id == assignment.configId }) return@mutate s
            s.copy(
                assignments = if (assignment is WgAssignment.SystemDefault) {
                    s.assignments - appIds.toSet()
                } else {
                    s.assignments + appIds.associateWith { assignment }
                },
            )
        }
    }

    override suspend fun generatePrivateKey(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(Backend.newWgPrivateKey().base64())
        } catch (e: Throwable) {
            // native library not available: a private key is just clamped random bytes
            try {
                Result.success(Base64.encodeToString(WgCurve25519.newPrivateKey(), Base64.NO_WRAP))
            } catch (e2: Throwable) {
                Result.failure(e2)
            }
        }
    }

    override suspend fun publicKeyOf(privateKey: String): String? = withContext(Dispatchers.IO) {
        val key = privateKey.trim()
        if (!WgValidator.isValidKey(key)) return@withContext null
        try {
            Backend.newWgPrivateKeyOf(key).mult().base64()
        } catch (e: Throwable) {
            try {
                Base64.encodeToString(WgCurve25519.publicKey(Base64.decode(key, Base64.DEFAULT)), Base64.NO_WRAP)
            } catch (e2: Throwable) {
                null
            }
        }
    }

    override fun generatePresharedKey(): String {
        val b = ByteArray(32)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    override fun routeFor(flow: FlowInfo): WgRoute {
        try {
            val s = state
            val configId = when (val a = s.assignments[flow.appId] ?: WgAssignment.SystemDefault) {
                WgAssignment.Direct -> return WgRoute.Direct
                is WgAssignment.Config -> a.configId
                WgAssignment.SystemDefault -> s.systemDefault ?: return WgRoute.Direct
            }
            val config = s.configs.firstOrNull { it.id == configId } ?: return WgRoute.Direct
            if (!config.enabled) return WgRoute.Direct
            if (config.mobileOnly && flow.environment.network != ConnectionType.Mobile) return WgRoute.Direct
            val st = _status.value[configId] ?: WgStatus.Off
            return when {
                st == WgStatus.Up || st == WgStatus.Connecting -> WgRoute.Via(config.proxyId)
                config.lockdown -> WgRoute.Block
                else -> WgRoute.Direct
            }
        } catch (e: Throwable) {
            return WgRoute.Direct
        }
    }

    // ---- engine ----

    override suspend fun onTunnelConnected(tunnel: Tunnel) {
        this.tunnel = tunnel
        _status.value = emptyMap()
        _stats.value = emptyMap()
        state.configs.filter { it.enabled }.forEach { applyToTunnel(it) }
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_MS)
                refreshStatus()
            }
        }
    }

    override suspend fun onTunnelDisconnected() {
        pollJob?.cancel()
        pollJob = null
        tunnel = null
        _status.value = emptyMap()
        _stats.value = emptyMap()
    }

    override suspend fun refreshStatus() {
        val t = tunnel ?: return
        engineLock.withLock {
            withContext(Dispatchers.IO) {
                val newStatus = HashMap<Int, WgStatus>()
                val newStats = HashMap<Int, WgStats>()
                for (c in state.configs) {
                    if (!c.enabled) continue
                    val read = readProxy(t, c)
                    newStatus[c.id] = read.first
                    read.second?.let { newStats[c.id] = it }
                }
                _status.value = newStatus
                _stats.value = newStats
            }
        }
    }

    /** Reads status and stats of one proxy; adds it again if the engine lost it. Never throws. */
    private fun readProxy(t: Tunnel, c: WireguardConfig): Pair<WgStatus, WgStats?> {
        return try {
            val proxies = t.getProxies()
            if (!proxies.hasProxy(c.proxyId)) {
                proxies.addProxy(c.proxyId, WgConfParser.toUserspace(c))
            }
            val proxy = proxies.getProxy(c.proxyId)
            val rs = try {
                proxy.router().stat()
            } catch (e: Throwable) {
                null
            }
            val stats = WgStats(
                rxBytes = rs?.rx ?: 0,
                txBytes = rs?.tx ?: 0,
                lastHandshakeMs = rs?.lastOK ?: 0,
                sinceMs = rs?.since ?: 0,
                lastError = rs?.lastErr.orEmpty(),
            )
            val st = mapStatus(proxy.status(), stats)
            if (st == WgStatus.Connecting || st == WgStatus.Down) {
                // a WireGuard peer only answers after traffic; probe so that the tunnel can recover
                try {
                    proxy.ping()
                } catch (e: Throwable) {
                    // ignore
                }
            }
            st to stats
        } catch (e: Throwable) {
            WgStatus.Error to null
        }
    }

    private fun mapStatus(code: Int, stats: WgStats): WgStatus {
        val now = System.currentTimeMillis()
        val everWorked = stats.lastHandshakeMs > 0
        val waitedTooLong = !everWorked && stats.sinceMs > 0 && now - stats.sinceMs > CONNECT_TIMEOUT_MS
        return when (code) {
            Backend.TOK -> WgStatus.Up
            Backend.TZZ, Backend.TUP -> when {
                everWorked && code == Backend.TZZ -> WgStatus.Up
                waitedTooLong -> WgStatus.Down
                else -> WgStatus.Connecting
            }
            Backend.TNT, Backend.TKO -> WgStatus.Down
            Backend.TPU -> WgStatus.Off
            else -> WgStatus.Error
        }
    }

    private suspend fun applyToTunnel(config: WireguardConfig) {
        val t = tunnel ?: return
        withContext(Dispatchers.IO) {
            engineLock.withLock {
                val result = try {
                    val proxy = t.getProxies().addProxy(config.proxyId, WgConfParser.toUserspace(config))
                    mapStatus(proxy.status(), WgStats())
                } catch (e: Throwable) {
                    WgStatus.Error
                }
                _status.value = _status.value + (config.id to result)
            }
        }
    }

    private suspend fun removeFromTunnel(id: Int) {
        engineLock.withLock {
            _status.value = _status.value - id
            _stats.value = _stats.value - id
            val t = tunnel ?: return
            withContext(Dispatchers.IO) {
                try {
                    t.getProxies().removeProxy("wg$id")
                } catch (e: Throwable) {
                    // already gone
                }
            }
        }
    }

    private companion object {
        const val POLL_MS = 5_000L
        const val CONNECT_TIMEOUT_MS = 30_000L
    }
}
