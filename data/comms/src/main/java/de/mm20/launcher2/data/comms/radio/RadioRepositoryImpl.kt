package de.mm20.launcher2.data.comms.radio

import android.content.Context
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.comms.repository.RadioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RadioRepositoryImpl(
    context: Context,
    private val apiClient: RadioBrowserClient
) : RadioRepository {

    private val dao = RadioDatabase.getDatabase(context).radioStationDao()

    override fun observeFavorites(): Flow<List<RadioStation>> {
        return dao.observeFavorites().map { entities ->
            entities.map { RadioStation(it.id, it.name, it.streamUrl, it.faviconUrl) }
        }
    }

    override suspend fun toggleFavorite(station: RadioStation) {
        val existing = dao.getStation(station.id)
        if (existing != null) {
            dao.delete(existing)
        } else {
            dao.insert(RadioStationEntity(station.id, station.name, station.streamUrl, station.faviconUrl))
        }
    }

    override suspend fun searchStations(query: String): List<RadioStation> {
        return apiClient.searchStations(query)
    }
}
