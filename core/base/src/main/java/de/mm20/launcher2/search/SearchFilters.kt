package de.mm20.launcher2.search

import de.mm20.launcher2.ktx.toInt
import kotlinx.serialization.Serializable

@Serializable
data class SearchFilters(
    val allowNetwork: Boolean = false,
    val hiddenItems: Boolean = false,
    val apps: Boolean = true,
    val websites: Boolean = true,
    val articles: Boolean = true,
    val places: Boolean = true,
    val files: Boolean = true,
    val shortcuts: Boolean = true,
    val contacts: Boolean = true,
    val events: Boolean = true,
    val reminders: Boolean = true,
    val tools: Boolean = true,
    val documents: Boolean = true,
    val images: Boolean = true,
    val video: Boolean = true,
    val music: Boolean = true,
) {
    private val categories = listOf(
        apps,
        websites,
        articles,
        places,
        files,
        shortcuts,
        contacts,
        events,
        reminders,
        tools,
        documents,
        images,
        video,
        music
    )

    val allCategoriesEnabled
        get() = categories.all { it }

    val enabledCategories: Int
        get() = categories.count { it }
}
