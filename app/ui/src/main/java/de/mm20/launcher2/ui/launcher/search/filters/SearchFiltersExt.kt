package de.mm20.launcher2.ui.launcher.search.filters

import de.mm20.launcher2.search.SearchFilters

fun SearchFilters.withAllCategories(): SearchFilters {
    return copy(
        apps = true,
        websites = true,
        articles = true,
        places = true,
        files = true,
        shortcuts = true,
        contacts = true,
        events = true,
        reminders = true,
        tools = true,
        documents = true,
        images = true,
        video = true,
        music = true,
        settings = true,
    )
}

fun SearchFilters.withOnlyCategory(
    apps: Boolean = false,
    websites: Boolean = false,
    articles: Boolean = false,
    places: Boolean = false,
    files: Boolean = false,
    shortcuts: Boolean = false,
    contacts: Boolean = false,
    events: Boolean = false,
    reminders: Boolean = false,
    utilities: Boolean = false,
    documents: Boolean = false,
    images: Boolean = false,
    video: Boolean = false,
    music: Boolean = false,
    settings: Boolean = false,
): SearchFilters {
    return copy(
        apps = apps,
        websites = websites,
        articles = articles,
        places = places,
        files = files,
        shortcuts = shortcuts,
        contacts = contacts,
        events = events,
        reminders = reminders,
        tools = utilities,
        documents = documents,
        images = images,
        video = video,
        music = music,
        settings = settings,
    )
}

private inline fun SearchFilters.toggleCategory(
    enabled: Boolean,
    update: SearchFilters.(Boolean) -> SearchFilters,
): SearchFilters = when {
    allCategoriesEnabled -> withOnlyCategory().update(true)
    enabled && enabledCategories == 1 -> withAllCategories()
    else -> update(!enabled)
}

fun SearchFilters.toggleApps(): SearchFilters = toggleCategory(apps) { copy(apps = it) }

fun SearchFilters.toggleWebsites(): SearchFilters = toggleCategory(websites) { copy(websites = it) }

fun SearchFilters.toggleArticles(): SearchFilters = toggleCategory(articles) { copy(articles = it) }

fun SearchFilters.togglePlaces(): SearchFilters = toggleCategory(places) { copy(places = it) }

fun SearchFilters.toggleFiles(): SearchFilters = toggleCategory(files) { copy(files = it) }

fun SearchFilters.toggleShortcuts(): SearchFilters = toggleCategory(shortcuts) { copy(shortcuts = it) }

fun SearchFilters.toggleContacts(): SearchFilters = toggleCategory(contacts) { copy(contacts = it) }

fun SearchFilters.toggleEvents(): SearchFilters = toggleCategory(events) { copy(events = it) }

fun SearchFilters.toggleTools(): SearchFilters = toggleCategory(tools) { copy(tools = it) }

fun SearchFilters.toggleReminders(): SearchFilters = toggleCategory(reminders) { copy(reminders = it) }

fun SearchFilters.toggleDocuments(): SearchFilters = toggleCategory(documents) { copy(documents = it) }

fun SearchFilters.toggleImages(): SearchFilters = toggleCategory(images) { copy(images = it) }

fun SearchFilters.toggleVideo(): SearchFilters = toggleCategory(video) { copy(video = it) }

fun SearchFilters.toggleMusic(): SearchFilters = toggleCategory(music) { copy(music = it) }

fun SearchFilters.toggleSettings(): SearchFilters = toggleCategory(settings) { copy(settings = it) }
