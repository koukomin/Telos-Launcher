package de.mm20.launcher2.data.comms.tv

import android.content.Context
import de.mm20.launcher2.comms.tv.TvCustomChannel
import de.mm20.launcher2.comms.tv.TvIds
import de.mm20.launcher2.comms.tv.TvImportResult
import de.mm20.launcher2.comms.tv.TvLimits
import de.mm20.launcher2.comms.tv.TvM3u
import de.mm20.launcher2.comms.tv.TvRecent
import de.mm20.launcher2.comms.tv.TvRepository
import de.mm20.launcher2.comms.tv.TvSaveResult
import de.mm20.launcher2.comms.tv.TvUrls
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TvRepositoryImpl(context: Context) : TvRepository {

    private val dao = TvDatabase.getDatabase(context).tvDao()
    private val writeLock = Mutex()

    private fun TvCustomEntity.toModel() = TvCustomChannel(id, name, streamUrl, logoUrl, groupName, addedAt)

    override fun observeFavorites(): Flow<List<String>> = dao.observeFavorites().map { l -> l.map { it.channelId } }

    override suspend fun isFavorite(channelId: String): Boolean = dao.favoriteCount(channelId) > 0

    override suspend fun setFavorite(channelId: String, favorite: Boolean) = writeLock.withLock {
        if (channelId.isBlank() || channelId.length > 200) return@withLock
        if (!favorite) {
            dao.deleteFavorite(channelId)
        } else if (dao.favoriteCount(channelId) == 0 && dao.totalFavorites() < TvLimits.MAX_FAVORITES) {
            dao.insertFavorite(TvFavoriteEntity(channelId, dao.maxFavoritePosition() + 1, System.currentTimeMillis()))
        }
    }

    override suspend fun moveFavorite(channelId: String, toIndex: Int) = writeLock.withLock {
        val ids = dao.getFavorites().map { it.channelId }.toMutableList()
        val from = ids.indexOf(channelId)
        if (from < 0) return@withLock
        ids.removeAt(from)
        ids.add(toIndex.coerceIn(0, ids.size), channelId)
        ids.forEachIndexed { i, id -> dao.setFavoritePosition(id, i) }
    }

    override fun observeRecents(): Flow<List<TvRecent>> =
        dao.observeRecents(TvLimits.MAX_RECENTS).map { l -> l.map { TvRecent(it.channelId, it.playedAt) } }

    override suspend fun markPlayed(channelId: String) {
        if (channelId.isBlank() || channelId.length > 200) return
        dao.insertRecent(TvRecentEntity(channelId, System.currentTimeMillis()))
        dao.trimRecents(TvLimits.MAX_RECENTS)
    }

    override suspend fun clearRecents() = dao.clearRecents()

    override fun observeCustomChannels(): Flow<List<TvCustomChannel>> = dao.observeCustom().map { l -> l.map { it.toModel() } }

    override suspend fun saveCustomChannel(channel: TvCustomChannel): TvSaveResult = writeLock.withLock {
        val name = channel.name.trim()
        if (name.isEmpty() || name.length > TvLimits.MAX_NAME) return@withLock TvSaveResult.INVALID_NAME
        val url = TvUrls.sanitize(channel.streamUrl) ?: return@withLock TvSaveResult.INVALID_STREAM_URL
        val logo = if (channel.logoUrl.isBlank()) "" else TvUrls.sanitize(channel.logoUrl)
            ?: return@withLock TvSaveResult.INVALID_LOGO_URL
        val existing = if (TvIds.isCustom(channel.id)) dao.getCustomById(channel.id) else null
        if (dao.getCustom().any { it.streamUrl == url && it.id != existing?.id }) return@withLock TvSaveResult.DUPLICATE_URL
        if (existing == null && dao.customCount() >= TvLimits.MAX_CUSTOM) return@withLock TvSaveResult.LIMIT_REACHED
        dao.insertCustom(
            TvCustomEntity(
                id = existing?.id ?: (TvIds.CUSTOM_PREFIX + UUID.randomUUID()),
                name = name,
                streamUrl = url,
                logoUrl = logo,
                groupName = channel.group.trim().take(TvLimits.MAX_GROUP),
                addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            )
        )
        TvSaveResult.SAVED
    }

    override suspend fun deleteCustomChannel(id: String) = writeLock.withLock {
        dao.deleteCustom(id)
        dao.deleteCustomFavorite(id)
        dao.deleteRecent(id)
        dao.deletePreferred(id)
    }

    override suspend fun importM3u(text: String): TvImportResult = writeLock.withLock {
        if (text.length > TvM3u.MAX_CHARS) return@withLock TvImportResult(0, 0, tooLarge = true)
        val parsed = TvM3u.parse(text)
        val known = dao.getCustom().mapTo(HashSet()) { it.streamUrl }
        val room = (TvLimits.MAX_CUSTOM - dao.customCount()).coerceAtLeast(0)
        var skipped = parsed.skipped
        val fresh = ArrayList<TvCustomEntity>()
        val now = System.currentTimeMillis()
        for (e in parsed.entries) {
            if (fresh.size >= room || !known.add(e.url)) {
                skipped++
                continue
            }
            fresh.add(TvCustomEntity(TvIds.CUSTOM_PREFIX + UUID.randomUUID(), e.name, e.url, e.logo, e.group, now))
        }
        if (fresh.isNotEmpty()) dao.insertCustomList(fresh)
        TvImportResult(added = fresh.size, skipped = skipped, truncated = parsed.truncated)
    }

    override suspend fun preferredStream(channelId: String): String? = dao.getPreferred(channelId)?.streamUrl

    override suspend fun setPreferredStream(channelId: String, url: String?) {
        val clean = TvUrls.sanitize(url)
        if (clean == null) dao.deletePreferred(channelId) else dao.insertPreferred(TvPreferredEntity(channelId, clean))
    }

    override suspend fun exportBackup(): String {
        val favorites = JSONArray()
        dao.getFavorites().forEach { favorites.put(it.channelId) }
        val recents = JSONArray()
        dao.getRecents().sortedByDescending { it.playedAt }.forEach {
            recents.put(JSONObject().put("id", it.channelId).put("at", it.playedAt))
        }
        val custom = JSONArray()
        dao.getCustom().forEach {
            custom.put(
                JSONObject().put("id", it.id).put("name", it.name).put("url", it.streamUrl)
                    .put("logo", it.logoUrl).put("group", it.groupName).put("addedAt", it.addedAt)
            )
        }
        val preferred = JSONObject()
        dao.getAllPreferred().forEach { preferred.put(it.channelId, it.streamUrl) }
        return JSONObject().put("version", 1).put("favorites", favorites).put("recents", recents)
            .put("custom", custom).put("preferred", preferred).toString(2)
    }

    override suspend fun restoreBackup(json: String): Int = writeLock.withLock {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return@withLock 0
        var restored = 0
        val known = dao.getCustom().mapTo(HashSet()) { it.streamUrl }
        var customCount = dao.customCount()
        val customs = root.optJSONArray("custom")
        for (i in 0 until (customs?.length() ?: 0)) {
            val o = customs?.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val url = TvUrls.sanitize(o.optString("url")) ?: continue
            val name = o.optString("name").trim().take(TvLimits.MAX_NAME)
            if (name.isEmpty() || !TvIds.isCustom(id) || id.length > 100) continue
            val existing = dao.getCustomById(id)
            if (existing == null) {
                if (!known.add(url) || customCount >= TvLimits.MAX_CUSTOM) continue
                customCount++
            }
            dao.insertCustom(
                TvCustomEntity(
                    id, name, url, TvUrls.sanitize(o.optString("logo")).orEmpty(),
                    o.optString("group").trim().take(TvLimits.MAX_GROUP), o.optLong("addedAt"),
                )
            )
            restored++
        }
        val favs = root.optJSONArray("favorites")
        for (i in 0 until (favs?.length() ?: 0)) {
            val id = favs?.optString(i).orEmpty()
            if (id.isBlank() || id.length > 200 || dao.favoriteCount(id) > 0 || dao.totalFavorites() >= TvLimits.MAX_FAVORITES) continue
            dao.insertFavorite(TvFavoriteEntity(id, dao.maxFavoritePosition() + 1, System.currentTimeMillis()))
            restored++
        }
        val recents = root.optJSONArray("recents")
        for (i in 0 until (recents?.length() ?: 0)) {
            val o = recents?.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank() || id.length > 200) continue
            dao.insertRecent(TvRecentEntity(id, o.optLong("at")))
        }
        dao.trimRecents(TvLimits.MAX_RECENTS)
        val preferred = root.optJSONObject("preferred")
        if (preferred != null) {
            val keys = preferred.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val url = TvUrls.sanitize(preferred.optString(id)) ?: continue
                if (id.length > 200) continue
                dao.insertPreferred(TvPreferredEntity(id, url))
            }
        }
        restored
    }
}
