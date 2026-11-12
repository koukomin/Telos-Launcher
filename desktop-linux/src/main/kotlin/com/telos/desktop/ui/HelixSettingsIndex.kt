package com.telos.desktop.ui

/**
 * Search index for Helix Settings cards: match query against card titles and toggle/labels.
 */
data class HelixSettingsIndexEntry(
    val id: String,
    val title: String,
    val keywords: List<String> = emptyList(),
)

object HelixSettingsIndex {
    fun matches(entry: HelixSettingsIndexEntry, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        if (entry.title.lowercase().contains(q)) return true
        return entry.keywords.any { it.lowercase().contains(q) }
    }

    fun filter(
        entries: List<HelixSettingsIndexEntry>,
        query: String,
    ): List<HelixSettingsIndexEntry> = entries.filter { matches(it, query) }

    fun filterIds(
        entries: List<HelixSettingsIndexEntry>,
        query: String,
    ): Set<String> = filter(entries, query).mapTo(LinkedHashSet()) { it.id }
}
