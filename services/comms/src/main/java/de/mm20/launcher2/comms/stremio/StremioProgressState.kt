package de.mm20.launcher2.comms.stremio

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** Stremio's public library-state units are milliseconds; watched and resume are distinct. */
object StremioProgressState {
    const val WATCHED_THRESHOLD = 0.7
    const val CREDITS_THRESHOLD = 0.9

    fun orderedVideoIds(videos: List<Video>): List<String> = videos.sortedWith(compareBy<Video>(
        { it.season ?: Int.MIN_VALUE }, { it.episode ?: Int.MIN_VALUE },
        { runCatching { Instant.parse(it.released).toEpochMilli() }.getOrDefault(Long.MIN_VALUE) },
    )).map { it.id }.distinct()

    /** Returns null for a stale update. Preserve all server fields not explicitly changed. */
    fun update(item: JSONObject, progress: WatchProgress): JSONObject? {
        require(progress.durationMs > 0 && progress.positionMs >= 0 && progress.timeWatchedMs >= 0)
        val result = JSONObject(item.toString())
        val state = result.optJSONObject("state") ?: JSONObject().also { result.put("state", it) }
        val serverTime = runCatching { Instant.parse(state.optString("lastWatched")).toEpochMilli() }.getOrDefault(0L)
        if (serverTime > progress.watchedAt) {
            // An older completed episode still adds watch evidence, but must not move the newer resume pointer.
            if (progress.timeWatchedMs.toDouble() <= progress.durationMs * WATCHED_THRESHOLD) return null
            if (progress.type == "movie") {
                if (state.optLong("timesWatched") > 0) return null
                state.put("timesWatched", 1)
            } else if (progress.type == "series") {
                val field = state.optString("watched").takeIf { it.isNotBlank() && it != "null" }
                if (progress.videoId in StremioWatched(videos = field).episodes(progress.videoIds)) return null
                val updated = markEpisode(field, progress.videoIds, progress.videoId)
                check(updated != null && updated != field) { "Episode metadata is needed to merge watched history" }
                state.put("watched", updated).put("timesWatched", state.optLong("timesWatched") + 1)
            } else return null
            result.put("_mtime", Instant.ofEpochMilli(maxOf(serverTime, System.currentTimeMillis())).toString())
            return result
        }
        val sameVideo = state.optString("video_id") == progress.videoId
        val oldTime = if (sameVideo) state.optLong("timeWatched").coerceAtLeast(0) else 0L
        val watchedTime = maxOf(oldTime, progress.timeWatchedMs)
        val wasFlagged = sameVideo && state.optInt("flaggedWatched") > 0
        val nowFlagged = wasFlagged || watchedTime.toDouble() > progress.durationMs * WATCHED_THRESHOLD
        if (!sameVideo) state.put("overallTimeWatched", state.optLong("overallTimeWatched") + state.optLong("timeWatched"))
        state.put("overallTimeWatched", state.optLong("overallTimeWatched") + (watchedTime - oldTime).coerceAtLeast(0))
            .put("video_id", progress.videoId).put("duration", progress.durationMs)
            .put("timeWatched", watchedTime).put("timeOffset", if (progress.completed) 0 else progress.positionMs.coerceAtMost(progress.durationMs))
            .put("lastWatched", Instant.ofEpochMilli(progress.watchedAt).toString()).put("flaggedWatched", if (nowFlagged) 1 else 0)
        if (nowFlagged && !wasFlagged) state.put("timesWatched", state.optLong("timesWatched") + 1)
        if (nowFlagged && progress.type == "series") {
            markEpisode(state.optString("watched").takeIf { it.isNotBlank() && it != "null" }, progress.videoIds, progress.videoId)
                ?.let { state.put("watched", it) }
        }
        result.put("_mtime", Instant.ofEpochMilli(progress.watchedAt).toString())
        return result
    }

    /** Set one bit without discarding bits outside the currently available metadata window. */
    fun markEpisode(existing: String?, videoIds: List<String>, videoId: String): String? = runCatching {
        val index = videoIds.indexOf(videoId)
        if (index < 0) return existing
        var anchor = videoId
        var length = index + 1
        var bytes = ByteArray((length + 7) / 8)
        var bit = index
        if (existing != null) {
            val prefix = existing.substringBeforeLast(':')
            anchor = prefix.substringBeforeLast(':')
            length = prefix.substringAfterLast(':').toInt()
            require(length in 1..1_000_000)
            val anchorIndex = videoIds.indexOf(anchor)
            if (anchorIndex < 0) return existing
            val decoded = InflaterInputStream(Base64.getDecoder().decode(existing.substringAfterLast(':')).inputStream()).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(output.size() + n <= 125_000)
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            bit = index + length - 1 - anchorIndex
            if (bit < 0) {
                val shift = -bit
                require(length + shift <= 1_000_000)
                bytes = ByteArray((length + shift + 7) / 8)
                for (i in 0 until length) if (i / 8 < decoded.size && (decoded[i / 8].toInt() and (1 shl (i % 8))) != 0) {
                    val next = i + shift; bytes[next / 8] = (bytes[next / 8].toInt() or (1 shl (next % 8))).toByte()
                }
                length += shift; bit = 0
            } else {
                if (bit >= length) { length = bit + 1; anchor = videoId }
                require(length <= 1_000_000)
                bytes = decoded.copyOf((length + 7) / 8)
            }
        }
        bytes[bit / 8] = (bytes[bit / 8].toInt() or (1 shl (bit % 8))).toByte()
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output).use { it.write(bytes) }
        "$anchor:$length:" + Base64.getEncoder().encodeToString(output.toByteArray())
    }.getOrDefault(existing)
}
