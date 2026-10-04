package de.mm20.launcher2.comms.repository

import de.mm20.launcher2.comms.model.RadioStation
import kotlinx.coroutines.flow.Flow

interface RadioRepository {
    fun observeFavorites(): Flow<List<RadioStation>>
    suspend fun toggleFavorite(station: RadioStation)
    suspend fun searchStations(query: String): List<RadioStation>
}
