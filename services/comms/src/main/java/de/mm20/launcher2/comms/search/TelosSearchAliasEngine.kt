package de.mm20.launcher2.comms.search

/**
 * Single Source of Truth for Search Acronyms and Multilingual Aliases across Telos Launcher.
 * Maps short keywords in English, Greek, and Greeklish (e.g. "wifi", "bt", "cam", "calc", "sec", "dial")
 * to normalized domain targets for 0ms instant matching.
 */
object TelosSearchAliasEngine {

    private val aliasMap: Map<String, String> = mapOf(
        // Connectivity shortcuts
        "wifi" to "network",
        "bt" to "bluetooth",
        "net" to "network",
        "δικτυο" to "network",
        "δικτυα" to "network",

        // Camera & Media shortcuts
        "cam" to "camera",
        "photo" to "photos",
        "pic" to "photos",
        "καμερα" to "camera",
        "εικονες" to "photos",
        "μουσικη" to "music",
        "βιντεο" to "video",

        // System & Security shortcuts
        "sec" to "security",
        "lock" to "applock",
        "calc" to "calculator",
        "αριθμομηχανη" to "calculator",
        "ρυθμισεις" to "settings",
        "επαφες" to "contacts",
        "τηλεφωνο" to "phone",
        "dial" to "phone"
    )

    /**
     * Resolves a query string to its canonical target alias if present, or returns the normalized query.
     */
    fun resolveAlias(query: String): String {
        val trimmed = query.trim().lowercase()
        return aliasMap[trimmed] ?: trimmed
    }
}
