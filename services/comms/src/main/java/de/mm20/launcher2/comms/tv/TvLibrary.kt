package de.mm20.launcher2.comms.tv

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Resolves the stored ids of [TvRepository] to channels, from the catalog [TvCatalog.index] and the
 * custom channels. Channels that are gone from the catalog are skipped. Emits again when the
 * catalog index or the stored data change.
 */
class TvLibrary(private val catalog: TvCatalog, private val repository: TvRepository) {

    /** Favorites in the user's order */
    fun observeFavorites(): Flow<List<TvChannel>> =
        combine(repository.observeFavorites(), repository.observeCustomChannels(), catalog.index) { ids, custom, index ->
            val customById = custom.associateBy { it.id }
            ids.mapNotNull { resolve(it, customById, index) }
        }

    /** Recently played channels, newest first */
    fun observeRecents(): Flow<List<TvChannel>> =
        combine(repository.observeRecents(), repository.observeCustomChannels(), catalog.index) { recents, custom, index ->
            val customById = custom.associateBy { it.id }
            recents.mapNotNull { resolve(it.channelId, customById, index) }
        }

    /** The user's custom channels as channels, sorted by name */
    fun observeCustomChannels(): Flow<List<TvChannel>> =
        repository.observeCustomChannels().map { list -> list.map { it.toChannel() }.sortedBy { it.name.lowercase() } }

    /** One channel by id (catalog or custom); the catalog index must have been opened for catalog ids */
    suspend fun channel(id: String): TvChannel? {
        val custom = repository.observeCustomChannels().first().associateBy { it.id }
        return resolve(id, custom, catalog.index.value)
    }

    private fun resolve(id: String, custom: Map<String, TvCustomChannel>, index: TvIndex?): TvChannel? =
        if (TvIds.isCustom(id)) custom[id]?.toChannel() else index?.channel(id)
}
