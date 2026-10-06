package de.mm20.launcher2.comms.repository

import de.mm20.launcher2.comms.model.RadioHistoryEntry
import de.mm20.launcher2.comms.model.RadioStation
import kotlinx.coroutines.flow.Flow

interface RadioRepository {
    /** All saved stations (the user's collection), sorted by name */
    fun observeFavorites(): Flow<List<RadioStation>>
    suspend fun toggleFavorite(station: RadioStation)
    suspend fun searchStations(query: String): List<RadioStation>

    suspend fun saveStation(station: RadioStation)
    suspend fun renameStation(id: String, name: String)
    suspend fun deleteStation(id: String)

    /** Imports stations from M3U / PLS text. Returns how many new stations were added. */
    suspend fun importPlaylist(text: String): Int
    suspend fun exportM3u(): String

    /** Full backup of the collection as JSON, and restore from it. Returns restored count. */
    suspend fun exportBackup(): String
    suspend fun restoreBackup(json: String): Int

    fun observeHistory(): Flow<List<RadioHistoryEntry>>
    suspend fun addHistory(stationId: String, stationName: String, title: String)
    suspend fun clearHistory()

    /** Lets radio-browser.info know a station was played (their community click counter) */
    suspend fun countClick(stationId: String)
}
