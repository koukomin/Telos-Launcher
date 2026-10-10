package de.mm20.launcher2.ui.media.music

import android.content.Context

/**
 * A tiny play history: how often and when each track (media store id) was started. Kept in its own
 * preferences file, local only; it feeds "Recently played" and "Quick picks". Additive, nothing else reads it.
 */
object MusicHistory {
    private const val PREFS = "telos_music_history"
    private const val KEY = "plays"
    private const val MAX = 2000

    data class Entry(val count: Int, val lastPlayedMs: Long)

    fun load(context: Context): Map<Long, Entry> = runCatching {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null).orEmpty()
        raw.lineSequence().mapNotNull { line ->
            val p = line.split(',')
            if (p.size != 3) return@mapNotNull null
            val id = p[0].toLongOrNull() ?: return@mapNotNull null
            val c = p[1].toIntOrNull() ?: return@mapNotNull null
            val t = p[2].toLongOrNull() ?: return@mapNotNull null
            id to Entry(c, t)
        }.toMap()
    }.getOrDefault(emptyMap())

    /** Counts one play of [id] and returns the new history */
    fun record(context: Context, id: Long, now: Long = System.currentTimeMillis()): Map<Long, Entry> {
        val map = load(context).toMutableMap()
        map[id] = Entry((map[id]?.count ?: 0) + 1, now)
        val kept = if (map.size > MAX) map.entries.sortedByDescending { it.value.lastPlayedMs }.take(MAX).associate { it.key to it.value } else map
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, kept.entries.joinToString("\n") { "${it.key},${it.value.count},${it.value.lastPlayedMs}" })
                .apply()
        }
        return kept
    }
}
