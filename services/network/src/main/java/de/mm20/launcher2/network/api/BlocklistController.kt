package de.mm20.launcher2.network.api

import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.StateFlow

/** What a blocklist group is about. */
enum class BlocklistCategory { Ads, Trackers, Malware, Adult, Social, Gambling, Other }

/** One downloadable list of domains. */
@Serializable
data class Blocklist(
    /** Stable id, also used in [FlowInfo.blocklists] and the counters. */
    val id: String,
    val name: String,
    val description: String = "",
    /** Number of entries, 0 when unknown. */
    val entryCount: Long = 0,
    /** Where the list comes from. */
    val url: String = "",
)

/** A group of lists the user switches on and off together or one by one. */
@Serializable
data class BlocklistGroup(
    val id: String,
    val name: String,
    val category: BlocklistCategory,
    val description: String = "",
    val lists: List<Blocklist>,
    /** Top level section: `parentalcontrol`, `security` or `privacy` (Rethink DNS naming). */
    val section: String = "",
)

/** A trusted domain: queries for it skip the blocklists. */
@Serializable
data class BlocklistBypass(
    val id: Long,
    val scope: RuleScope,
    /** Exact name or `*.example.com`. */
    val domain: String,
)

/** State of the list download. */
sealed interface BlocklistUpdateState {
    data object Idle : BlocklistUpdateState

    /** [progress] 0f..1f, or negative when unknown. */
    data class Downloading(val progress: Float) : BlocklistUpdateState

    data class Failed(val message: String) : BlocklistUpdateState
}

/**
 * Domain blocklists applied by the DNS layer of the engine. The engine hands the files to the Go
 * resolver in [EngineComponent.onTunnelConnected].
 */
interface BlocklistController : EngineComponent {
    /** All groups available for download. Empty until the catalog was loaded. */
    val groups: StateFlow<List<BlocklistGroup>>

    /** Ids of the enabled lists ([Blocklist.id]). */
    val enabled: StateFlow<Set<String>>

    /** Switches one list on or off and applies it to a running tunnel. */
    suspend fun setEnabled(listId: String, enabled: Boolean)

    /** True when the list files are downloaded and can be applied. */
    val installed: StateFlow<Boolean>

    /** True when the server announced a newer version than the downloaded one (set by [checkForUpdate]). */
    val updateAvailable: StateFlow<Boolean>

    /** Asks the server whether a newer version exists. Returns true when it does. */
    suspend fun checkForUpdate(): Result<Boolean>

    /** Checks daily in the background (WorkManager, unmetered network) and downloads when newer. Only after the first download. */
    val autoUpdate: StateFlow<Boolean>

    suspend fun setAutoUpdate(enabled: Boolean)

    /** Deletes the downloaded files and switches the engine to no blocklists. */
    suspend fun removeDownloaded()

    /** Disk space used by the downloaded files. */
    val storageBytes: StateFlow<Long>

    /** Switches a whole group. */
    suspend fun setGroupEnabled(groupId: String, enabled: Boolean)

    val updateState: StateFlow<BlocklistUpdateState>

    /** Epoch millis of the last successful download, 0 if never. */
    val lastUpdatedMs: StateFlow<Long>

    /**
     * Downloads the catalog and the lists if there is a newer version (or always when [force]).
     * Resolves when finished; progress is in [updateState].
     */
    suspend fun update(force: Boolean = false): Result<Unit>

    /** How many queries each list blocked since the counters were reset, by [Blocklist.id]. */
    val blockCounts: StateFlow<Map<String, Long>>

    /** Called by the engine when the resolver blocked a query because of these lists. Must be fast. */
    fun recordBlock(listIds: Collection<String>)

    suspend fun resetCounts()

    /** Trusted domains, per app or system-wide. */
    val bypass: StateFlow<List<BlocklistBypass>>

    suspend fun addBypass(scope: RuleScope, domain: String): Result<BlocklistBypass>

    suspend fun removeBypass(id: Long)

    /** True when the user trusts [domain] for the app with [uid] (own entry or system-wide). Fast. */
    fun isBypassed(uid: Int, domain: String): Boolean
}
