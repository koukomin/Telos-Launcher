package de.mm20.launcher2.data.comms.radio

import android.content.Context
import de.mm20.launcher2.comms.model.RadioHistoryEntry
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.comms.radio.RadioPlaylists
import de.mm20.launcher2.comms.repository.RadioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.UUID

class RadioRepositoryImpl(
    context: Context,
    private val apiClient: RadioBrowserClient
) : RadioRepository {

    private val dao = RadioDatabase.getDatabase(context).radioStationDao()

    private fun RadioStationEntity.toModel() = RadioStation(
        id = id,
        name = name,
        streamUrl = streamUrl,
        faviconUrl = faviconUrl,
        homepage = homepage,
        streamContent = streamContent,
        nameManuallySet = nameManuallySet,
        alternateStreams = alternateStreams.lines().filter { it.isNotBlank() },
        lastPlayedAt = lastPlayedAt,
    )

    private fun RadioStation.toEntity(addedAt: Long, lastPlayed: Long = lastPlayedAt) = RadioStationEntity(
        id = id,
        name = name,
        streamUrl = streamUrl,
        faviconUrl = faviconUrl,
        homepage = homepage,
        streamContent = streamContent,
        nameManuallySet = nameManuallySet,
        alternateStreams = alternateStreams.joinToString("\n"),
        addedAt = addedAt,
        lastPlayedAt = lastPlayed,
    )

    override fun observeFavorites(): Flow<List<RadioStation>> {
        return dao.observeFavorites().map { entities -> entities.map { it.toModel() } }
    }

    override suspend fun toggleFavorite(station: RadioStation) {
        val existing = dao.getStation(station.id)
        if (existing != null) {
            dao.delete(existing)
        } else {
            dao.insert(station.toEntity(System.currentTimeMillis()))
        }
    }

    override suspend fun searchStations(query: String): List<RadioStation> {
        return apiClient.searchStations(query)
    }

    override suspend fun saveStation(station: RadioStation) {
        val existing = dao.getStation(station.id)
        dao.insert(station.toEntity(existing?.addedAt ?: System.currentTimeMillis(), existing?.lastPlayedAt ?: station.lastPlayedAt))
    }

    override suspend fun renameStation(id: String, name: String) {
        if (name.isNotBlank()) dao.rename(id, name.trim())
    }

    override suspend fun markPlayed(id: String) {
        dao.markPlayed(id, System.currentTimeMillis())
    }

    override suspend fun deleteStation(id: String) {
        dao.deleteById(id)
    }

    override suspend fun importPlaylist(text: String): Int {
        val known = dao.getAll().map { it.streamUrl }.toMutableSet()
        var added = 0
        for (entry in RadioPlaylists.parse(text)) {
            val url = entry.urls.firstOrNull() ?: continue
            if (!known.add(url)) continue
            val name = entry.name.ifBlank { runCatching { URL(url).host }.getOrDefault(url) }
            dao.insert(
                RadioStation(
                    id = "local-" + UUID.randomUUID(),
                    name = name,
                    streamUrl = url,
                    faviconUrl = "",
                    alternateStreams = entry.urls.drop(1),
                ).toEntity(System.currentTimeMillis())
            )
            added++
        }
        return added
    }

    override suspend fun exportM3u(): String {
        val stations = dao.getAll().sortedBy { it.name.lowercase() }.map { it.toModel() }
        return RadioPlaylists.createM3u(stations)
    }

    override suspend fun exportBackup(): String {
        val array = JSONArray()
        for (s in dao.getAll()) {
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("streamUrl", s.streamUrl)
                    .put("faviconUrl", s.faviconUrl)
                    .put("homepage", s.homepage)
                    .put("streamContent", s.streamContent)
                    .put("nameManuallySet", s.nameManuallySet)
                    .put("alternateStreams", JSONArray(s.alternateStreams.lines().filter { it.isNotBlank() }))
                    .put("addedAt", s.addedAt)
                    .put("lastPlayedAt", s.lastPlayedAt)
            )
        }
        return JSONObject().put("version", 1).put("stations", array).toString(2)
    }

    override suspend fun restoreBackup(json: String): Int {
        val array = JSONObject(json).optJSONArray("stations") ?: return 0
        var restored = 0
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val id = o.optString("id").ifBlank { "local-" + UUID.randomUUID() }
            val url = o.optString("streamUrl")
            if (url.isBlank()) continue
            val alternates = o.optJSONArray("alternateStreams")
            dao.insert(
                RadioStationEntity(
                    id = id,
                    name = o.optString("name").ifBlank { url },
                    streamUrl = url,
                    faviconUrl = o.optString("faviconUrl"),
                    homepage = o.optString("homepage"),
                    streamContent = o.optString("streamContent"),
                    nameManuallySet = o.optBoolean("nameManuallySet"),
                    alternateStreams = (0 until (alternates?.length() ?: 0))
                        .joinToString("\n") { alternates!!.getString(it) },
                    addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                    lastPlayedAt = o.optLong("lastPlayedAt", 0L),
                )
            )
            restored++
        }
        return restored
    }

    override fun observeHistory(): Flow<List<RadioHistoryEntry>> {
        return dao.observeHistory().map { list ->
            list.map { RadioHistoryEntry(it.id, it.stationId, it.stationName, it.title, it.playedAt) }
        }
    }

    override suspend fun addHistory(stationId: String, stationName: String, title: String) {
        dao.insertHistory(
            RadioHistoryEntity(
                stationId = stationId,
                stationName = stationName,
                title = title,
                playedAt = System.currentTimeMillis(),
            )
        )
        dao.trimHistory()
    }

    override suspend fun clearHistory() {
        dao.clearHistory()
    }

    override suspend fun countClick(stationId: String) {
        if (stationId.startsWith("local-")) return
        apiClient.countClick(stationId)
    }
}
