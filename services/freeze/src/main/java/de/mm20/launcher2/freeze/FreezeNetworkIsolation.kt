package de.mm20.launcher2.freeze

import android.content.Context
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.AppRule
import de.mm20.launcher2.network.api.FirewallController
import java.util.concurrent.ConcurrentHashMap

/**
 * Telos Network block/restore used while Store or Play updates a frozen app.
 * [AutoFreezeController] keeps the public API as one-line delegates.
 */
internal class FreezeNetworkIsolation(
    private val context: Context,
    private val firewall: FirewallController,
    private val apps: AppDirectory,
    private val networkEngine: NetworkEngine,
) {
    private val storeUpdateNetRestore = ConcurrentHashMap<String, Boolean>()

    suspend fun isolateNetworkForUpdate(packageName: String) {
        val saved = blockNetworkForPackages(listOf(packageName))
        storeUpdateNetRestore.putAll(saved)
    }

    suspend fun endNetworkIsolation(packageName: String) {
        val previous = storeUpdateNetRestore.remove(packageName) ?: return
        restoreNetworkForPackages(mapOf(packageName to previous))
    }

    suspend fun blockNetworkForPackages(packages: Collection<String>): Map<String, Boolean> {
        if (packages.isEmpty()) return emptyMap()
        if (networkEngine.state.value !is NetState.On) return emptyMap()
        return applyPlayNetworkBlock(packages.toSet())
    }

    suspend fun restoreNetworkForPackages(saved: Map<String, Boolean>) {
        if (saved.isEmpty()) return
        for ((pkg, hadBlockAll) in saved) {
            if (hadBlockAll) continue
            val appId = appIdOf(pkg) ?: continue
            val existing = firewall.appRules.value[appId] ?: continue
            if (existing.blockAll) firewall.setAppRule(existing.copy(blockAll = false))
        }
    }

    suspend fun applyPlayNetworkBlock(packages: Set<String>): Map<String, Boolean> {
        val result = HashMap<String, Boolean>()
        for (pkg in packages) {
            val appId = appIdOf(pkg) ?: continue
            val existing = firewall.appRules.value[appId] ?: AppRule(appId)
            result[pkg] = existing.blockAll
            if (!existing.blockAll) {
                firewall.setAppRule(existing.copy(blockAll = true))
            }
        }
        return result
    }

    suspend fun unblockIfWeBlocked(packageName: String, hadBlockAll: Boolean) {
        if (hadBlockAll) return
        val appId = appIdOf(packageName) ?: return
        val existing = firewall.appRules.value[appId]
        if (existing != null && existing.blockAll) {
            firewall.setAppRule(existing.copy(blockAll = false))
        }
    }

    private fun appIdOf(pkg: String): Int? =
        apps.byPackage(pkg)?.appId ?: runCatching {
            context.packageManager.getApplicationInfo(pkg, 0).uid % 100000
        }.getOrNull()
}
