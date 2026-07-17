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
        music = true
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
    music: Boolean = false
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
        music = music
    )
}

/**
 * Create a new [SearchFilters] object with the [apps] property update, according to the following rules:
 *  - If all categories are enabled, disable all categories except for apps.
 *  - If apps is the only enabled category, enable all categories.
 *  - Otherwise, toggle the apps category.
 */
fun SearchFilters.toggleApps(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(apps = true)
    }
    if (apps && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(apps = !apps)
}

fun SearchFilters.toggleWebsites(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(websites = true)
    }
    if (websites && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(websites = !websites)
}

fun SearchFilters.toggleArticles(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(articles = true)
    }
    if (articles && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(articles = !articles)
}

fun SearchFilters.togglePlaces(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(places = true)
    }
    if (places && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(places = !places)
}

fun SearchFilters.toggleFiles(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(files = true)
    }
    if (files && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(files = !files)
}

fun SearchFilters.toggleShortcuts(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(shortcuts = true)
    }
    if (shortcuts && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(shortcuts = !shortcuts)
}

fun SearchFilters.toggleContacts(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(contacts = true)
    }
    if (contacts && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(contacts = !contacts)
}

fun SearchFilters.toggleEvents(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(events = true)
    }
    if (events && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(events = !events)
}

fun SearchFilters.toggleTools(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(utilities = true)
    }
    if (tools && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(tools = !tools)
}

fun SearchFilters.toggleReminders(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(reminders = true)
    }
    if (reminders && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(reminders = !reminders)
}

fun SearchFilters.toggleDocuments(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(documents = true)
    }
    if (documents && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(documents = !documents)
}

fun SearchFilters.toggleImages(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(images = true)
    }
    if (images && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(images = !images)
}

fun SearchFilters.toggleVideo(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(video = true)
    }
    if (video && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(video = !video)
}

fun SearchFilters.toggleMusic(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(music = true)
    }
    if (music && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(music = !music)
}
