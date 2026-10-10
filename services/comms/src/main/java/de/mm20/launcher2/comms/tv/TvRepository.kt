package de.mm20.launcher2.comms.tv

import kotlinx.coroutines.flow.Flow

/**
 * The user's own TV data (Room database telos_tv.db, implemented in :data:comms): favorites, recents,
 * custom channels and the preferred stream per channel. Channel ids are catalog ids or custom ids
 * (see [TvIds]). Favorites and recents may refer to catalog channels that no longer exist; resolve
 * them with [TvLibrary], which skips those.
 */
interface TvRepository {
    /** Favorite channel ids in the order the user arranged them (new ones at the end) */
    fun observeFavorites(): Flow<List<String>>
    suspend fun isFavorite(channelId: String): Boolean
    /** Adds or removes a favorite. Adding is ignored beyond [TvLimits.MAX_FAVORITES]. */
    suspend fun setFavorite(channelId: String, favorite: Boolean)
    /** Moves a favorite to a new position (0 based, clamped) */
    suspend fun moveFavorite(channelId: String, toIndex: Int)

    /** Recently played channels, newest first, at most [TvLimits.MAX_RECENTS] */
    fun observeRecents(): Flow<List<TvRecent>>
    /** Called by the player when a channel starts */
    suspend fun markPlayed(channelId: String)
    suspend fun clearRecents()

    fun observeCustomChannels(): Flow<List<TvCustomChannel>>
    /** Creates the channel when [TvCustomChannel.id] is blank or unknown, otherwise updates it. Validates names and http(s) addresses. */
    suspend fun saveCustomChannel(channel: TvCustomChannel): TvSaveResult
    suspend fun deleteCustomChannel(id: String)

    /**
     * Imports an M3U playlist into custom channels (EXTINF name, tvg-logo, group-title). Text above
     * [TvM3u.MAX_CHARS] is refused, at most [TvM3u.MAX_ENTRIES] entries are read, known addresses are skipped.
     */
    suspend fun importM3u(text: String): TvImportResult

    /** The stream URL the user chose for a channel, or null */
    suspend fun preferredStream(channelId: String): String?
    /** Remembers the stream (null forgets it) */
    suspend fun setPreferredStream(channelId: String, url: String?)

    /** JSON of everything above (see [TvBackup] for the combined file with the settings) */
    suspend fun exportBackup(): String
    /** Merges a JSON made by [exportBackup]; invalid entries are skipped. Returns the number of restored items. */
    suspend fun restoreBackup(json: String): Int
}
