package de.mm20.launcher2.network.impl

import android.content.Context
import com.celzero.firestack.intra.Tunnel
import de.mm20.launcher2.network.api.BlocklistBypass
import de.mm20.launcher2.network.api.BlocklistController
import de.mm20.launcher2.network.api.BlocklistGroup
import de.mm20.launcher2.network.api.BlocklistUpdateState
import de.mm20.launcher2.network.api.FlowInfo
import de.mm20.launcher2.network.api.RuleScope
import de.mm20.launcher2.network.util.IpUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
internal data class BlocklistState(
    val enabled: Set<String> = emptySet(),
    val bypass: List<BlocklistBypass> = emptyList(),
    val nextId: Long = 1,
    val lastUpdatedMs: Long = 0,
)

/**
 * Minimal default: remembers which lists are enabled and the trusted domains, counts blocks, and
 * has an empty catalog, so no list can be downloaded yet. A full implementation replaces it and
 * hands the downloaded files to the Go resolver in [onTunnelConnected].
 */
internal class DefaultBlocklistController(context: Context) : BlocklistController {
    private val store = de.mm20.launcher2.network.util.PersistedState(
        file = File(File(context.filesDir, "network"), "blocklists.json"),
        serializer = BlocklistState.serializer(),
        default = { BlocklistState() },
    )

    private val _groups = MutableStateFlow<List<BlocklistGroup>>(emptyList())
    override val groups: StateFlow<List<BlocklistGroup>> = _groups

    private val _enabled = MutableStateFlow(store.value.enabled)
    override val enabled: StateFlow<Set<String>> = _enabled

    private val _updateState = MutableStateFlow<BlocklistUpdateState>(BlocklistUpdateState.Idle)
    override val updateState: StateFlow<BlocklistUpdateState> = _updateState

    private val _lastUpdated = MutableStateFlow(store.value.lastUpdatedMs)
    override val lastUpdatedMs: StateFlow<Long> = _lastUpdated

    private val counters = ConcurrentHashMap<String, Long>()
    private val _blockCounts = MutableStateFlow<Map<String, Long>>(emptyMap())
    override val blockCounts: StateFlow<Map<String, Long>> = _blockCounts

    private val _bypass = MutableStateFlow(store.value.bypass)
    override val bypass: StateFlow<List<BlocklistBypass>> = _bypass

    override suspend fun setEnabled(listId: String, enabled: Boolean) {
        val state = store.update { it.copy(enabled = if (enabled) it.enabled + listId else it.enabled - listId) }
        _enabled.value = state.enabled
    }

    override suspend fun setGroupEnabled(groupId: String, enabled: Boolean) {
        val ids = _groups.value.firstOrNull { it.id == groupId }?.lists?.map { it.id }.orEmpty()
        val state = store.update { it.copy(enabled = if (enabled) it.enabled + ids else it.enabled - ids.toSet()) }
        _enabled.value = state.enabled
    }

    override suspend fun update(force: Boolean): Result<Unit> {
        // No catalog in the default implementation, so there is nothing to download
        _updateState.value = BlocklistUpdateState.Idle
        return Result.success(Unit)
    }

    override fun recordBlock(listIds: Collection<String>) {
        if (listIds.isEmpty()) return
        listIds.forEach { id -> counters.merge(id, 1L) { a, b -> a + b } }
        _blockCounts.value = HashMap(counters)
    }

    override suspend fun resetCounts() {
        counters.clear()
        _blockCounts.value = emptyMap()
    }

    override suspend fun addBypass(scope: RuleScope, domain: String): Result<BlocklistBypass> {
        val d = IpUtil.normalizeDomain(domain)
        if (d.isEmpty() || d.contains(' ') || d.contains('/')) {
            return Result.failure(IllegalArgumentException("invalid domain"))
        }
        var created: BlocklistBypass? = null
        val state = store.update { old ->
            val entry = BlocklistBypass(old.nextId, scope, d)
            created = entry
            old.copy(bypass = old.bypass + entry, nextId = old.nextId + 1)
        }
        _bypass.value = state.bypass
        return Result.success(created!!)
    }

    override suspend fun removeBypass(id: Long) {
        val state = store.update { it.copy(bypass = it.bypass.filterNot { b -> b.id == id }) }
        _bypass.value = state.bypass
    }

    override fun isBypassed(uid: Int, domain: String): Boolean {
        val d = IpUtil.normalizeDomain(domain)
        val appId = if (uid < 0) -1 else uid % FlowInfo.PER_USER_RANGE
        return _bypass.value.any { b ->
            val scopeMatches = when (val s = b.scope) {
                RuleScope.System -> true
                is RuleScope.App -> s.appId == appId
            }
            scopeMatches && domainMatches(b.domain, d)
        }
    }

    override suspend fun onTunnelConnected(tunnel: Tunnel) {
        // Nothing to hand to the resolver in the default implementation
    }

    companion object {
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
